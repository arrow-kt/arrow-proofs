package arrow.inject.compiler.plugin.fir.resolution.resolver

import arrow.inject.compiler.plugin.fir.FirAbstractProofComponent
import arrow.inject.compiler.plugin.fir.ProofKey
import arrow.inject.compiler.plugin.fir.coneType
import arrow.inject.compiler.plugin.model.Proof
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.typeParameterSymbols
import org.jetbrains.kotlin.fir.builder.buildPackageDirective
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirFile
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.FirResolvePhase
import org.jetbrains.kotlin.fir.declarations.builder.buildFile
import org.jetbrains.kotlin.fir.expressions.FirEmptyArgumentList
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirFunctionCallOrigin
import org.jetbrains.kotlin.fir.expressions.buildResolvedArgumentList
import org.jetbrains.kotlin.fir.expressions.builder.buildArgumentList
import org.jetbrains.kotlin.fir.expressions.builder.buildFunctionCall
import org.jetbrains.kotlin.fir.moduleData
import org.jetbrains.kotlin.fir.references.builder.buildResolvedNamedReference
import org.jetbrains.kotlin.fir.resolve.ScopeSession
import org.jetbrains.kotlin.fir.resolve.calls.CallInfo
import org.jetbrains.kotlin.fir.resolve.calls.CallKind
import org.jetbrains.kotlin.fir.resolve.calls.Candidate
import org.jetbrains.kotlin.fir.resolve.calls.CandidateFactory
import org.jetbrains.kotlin.fir.resolve.calls.FirNamedReferenceWithCandidate
import org.jetbrains.kotlin.fir.resolve.calls.ResolutionStageRunner
import org.jetbrains.kotlin.fir.resolve.inference.FirCallCompleter
import org.jetbrains.kotlin.fir.resolve.transformers.body.resolve.FirBodyResolveTransformer
import org.jetbrains.kotlin.fir.scopes.impl.toConeType
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirTypeParameterSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.builder.buildResolvedTypeRef
import org.jetbrains.kotlin.fir.types.builder.buildTypeProjectionWithVariance
import org.jetbrains.kotlin.fir.types.toFirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.type
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.calls.tasks.ExplicitReceiverKind
import org.jetbrains.kotlin.resolve.calls.tower.CandidateApplicability

internal class ProofResolutionStageRunner(
  override val session: FirSession,
) : FirAbstractProofComponent {

  private val resultResolutionStageRunner: ResolutionStageRunner by lazy { ResolutionStageRunner() }

  private val firBodyResolveTransformer: FirBodyResolveTransformer by lazy {
    FirBodyResolveTransformer(
      session = session,
      phase = FirResolvePhase.BODY_RESOLVE,
      implicitTypeOnly = false,
      scopeSession = scopeSession,
    )
  }

  private val firCallCompleter: FirCallCompleter by lazy {
    FirCallCompleter(firBodyResolveTransformer, firBodyResolveTransformer.components)
  }

  private val scopeSession = ScopeSession()

  fun List<Proof>.matchingCandidates(type: ConeKotlinType): Set<Candidate> {
    val resolveCallInfo = resolveCallInfo()

    val candidateFactory =
      CandidateFactory(firBodyResolveTransformer.resolutionContext, resolveCallInfo)

    return mapNotNull { proof ->
        val proofDeclaration: FirDeclaration = proof.declaration
        val proofCallInfo = proof.proofCallInfo(proofDeclaration, type)

        val candidate: Candidate = candidate(candidateFactory, proofCallInfo, proof)

        val candidateApplicability = candidate.applicability()

        val isCandidateApplicability = candidateApplicability == CandidateApplicability.RESOLVED

        if (isCandidateApplicability) {

          val firFunctionCall = proof.firFunctionCall(type, candidate)

          val (_: FirFunctionCall, isCallCompleted: Boolean) =
            firCallCompleter.completeCall(
              call = firFunctionCall,
              expectedTypeRef = type.toFirResolvedTypeRef(),
              expectedTypeMismatchIsReportedInChecker = true
            )

          if (isCallCompleted && candidate.errors.isEmpty()) candidate else null
        } else null
      }
      .toSet()
  }

  private fun Proof.firFunctionCall(type: ConeKotlinType, candidate: Candidate): FirFunctionCall =
    buildFunctionCall {
      typeRef = declaration.coneType.toFirResolvedTypeRef()
      argumentList = buildResolvedArgumentList(LinkedHashMap())
      typeArguments +=
        (declaration as? FirFunction)
          ?.typeParameters
          ?.map {
            buildTypeProjectionWithVariance {
              val targetTypeRefArg = targetTypeRef(type, it.toConeType())
              typeRef = targetTypeRefArg
              variance = it.symbol.variance
            }
          }
          .orEmpty()
          .toMutableList()
      calleeReference =
        FirNamedReferenceWithCandidate(
          source = declaration.source,
          name = (declaration.symbol as? FirCallableSymbol)?.name ?: Name.identifier("Unsupported"),
          candidate = candidate,
        )
    }

  private fun Proof.substitutionMap(
    expectedType: ConeKotlinType
  ): Map<FirTypeParameterSymbol, ConeKotlinType> =
    (declaration as? FirFunction)
      ?.typeParameters
      ?.zip(expectedType.typeArguments)
      .orEmpty()
      .mapNotNull { (tp1, tp2) ->
        val targetType = tp2.type
        if (targetType != null) tp1.symbol to targetType else null
      }
      .toMap()

  private fun Candidate.applicability(): CandidateApplicability =
    resultResolutionStageRunner.processCandidate(
      candidate = this,
      context = firBodyResolveTransformer.resolutionContext,
      stopOnFirstError = true,
    )

  private fun candidate(candidateFactory: CandidateFactory, proofCallInfo: CallInfo, proof: Proof) =
    candidateFactory.createCandidate(
      callInfo = proofCallInfo,
      symbol = proof.declaration.symbol,
      explicitReceiverKind = ExplicitReceiverKind.NO_EXPLICIT_RECEIVER,
      scope = null,
      dispatchReceiverValue = null,
      givenExtensionReceiverOptions = emptyList(),
      objectsByName = false,
    )

  private fun Proof.proofCallInfo(
    proofDeclaration: FirDeclaration,
    type: ConeKotlinType
  ): CallInfo =
    CallInfo(
      callSite = declaration,
      callKind =
        when (declaration) {
          is FirFunction -> CallKind.Function
          else -> CallKind.VariableAccess
        },
      name = (declaration.symbol as? FirCallableSymbol)?.name ?: Name.identifier("Unsupported"),
      explicitReceiver = null,
      argumentList =
        if (proofDeclaration is FirFunction) {
          buildArgumentList {
            arguments +=
              (proofDeclaration).symbol.valueParameterSymbols.map { valueParameter ->
                val targetTypeRef = targetTypeRef(type, valueParameter.resolvedReturnTypeRef.type)
                buildFunctionCall {
                  typeRef = targetTypeRef
                  argumentList = buildResolvedArgumentList(LinkedHashMap())
                  typeArguments +=
                    valueParameter.typeParameterSymbols.map {
                      buildTypeProjectionWithVariance {
                        val targetTypeRefArg = targetTypeRef(type, it.toConeType())
                        typeRef = targetTypeRefArg
                        variance = it.variance
                      }
                    }
                  calleeReference = buildResolvedNamedReference {
                    name = resolve.name
                    resolvedSymbol = resolve.symbol
                  }
                }
              }
          }
        } else FirEmptyArgumentList,
      isImplicitInvoke = false,
      typeArguments =
        declaration.symbol.typeParameterSymbols.orEmpty().map { typeParameterSymbol ->
          buildTypeProjectionWithVariance {
            val targetTypeRef = targetTypeRef(type, typeParameterSymbol.toConeType())
            typeRef = buildResolvedTypeRef { this.type = targetTypeRef.type }
            variance = typeParameterSymbol.variance
          }
        },
      session = session,
      containingFile = fakeFirFile,
      containingDeclarations = emptyList(),
      origin = FirFunctionCallOrigin.Regular,
    )

  private fun resolveCallInfo() =
    CallInfo(
      callSite = resolve, // TODO check generics
      callKind = CallKind.Function,
      name = resolve.name,
      explicitReceiver = null,
      argumentList = FirEmptyArgumentList,
      isImplicitInvoke = false,
      typeArguments = emptyList(),
      session = session,
      containingFile = fakeFirFile,
      containingDeclarations = emptyList(),
      origin = FirFunctionCallOrigin.Regular,
    )

  private val fakeFirFile: FirFile
    get() = buildFile {
      moduleData = session.moduleData
      origin = ProofKey.origin
      packageDirective = buildPackageDirective { packageFqName = FqName("PROOF_FAKE") }
      name = "PROOF_FAKE"
    }
}
