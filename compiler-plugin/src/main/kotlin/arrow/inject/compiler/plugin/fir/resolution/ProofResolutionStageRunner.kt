@file:OptIn(SymbolInternals::class)

package arrow.inject.compiler.plugin.fir.resolution

import arrow.inject.compiler.plugin.fir.FirAbstractProofComponent
import arrow.inject.compiler.plugin.fir.ProofKey
import arrow.inject.compiler.plugin.model.Proof
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.typeParameterSymbols
import org.jetbrains.kotlin.fir.builder.buildPackageDirective
import org.jetbrains.kotlin.fir.declarations.FirConstructor
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirFile
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.FirResolvePhase
import org.jetbrains.kotlin.fir.declarations.FirSimpleFunction
import org.jetbrains.kotlin.fir.declarations.builder.buildFile
import org.jetbrains.kotlin.fir.expressions.FirEmptyArgumentList
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirFunctionCallOrigin
import org.jetbrains.kotlin.fir.expressions.builder.buildFunctionCall
import org.jetbrains.kotlin.fir.moduleData
import org.jetbrains.kotlin.fir.resolve.ScopeSession
import org.jetbrains.kotlin.fir.resolve.calls.CallInfo
import org.jetbrains.kotlin.fir.resolve.calls.CallKind
import org.jetbrains.kotlin.fir.resolve.calls.Candidate
import org.jetbrains.kotlin.fir.resolve.calls.CandidateFactory
import org.jetbrains.kotlin.fir.resolve.calls.FirNamedReferenceWithCandidate
import org.jetbrains.kotlin.fir.resolve.calls.ResolutionStageRunner
import org.jetbrains.kotlin.fir.resolve.defaultType
import org.jetbrains.kotlin.fir.resolve.inference.FirCallCompleter
import org.jetbrains.kotlin.fir.resolve.substitution.ConeSubstitutor
import org.jetbrains.kotlin.fir.resolve.transformers.body.resolve.FirBodyResolveTransformer
import org.jetbrains.kotlin.fir.scopes.impl.toConeType
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.builder.buildResolvedTypeRef
import org.jetbrains.kotlin.fir.types.builder.buildTypeProjectionWithVariance
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.toFirResolvedTypeRef
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.calls.tasks.ExplicitReceiverKind
import org.jetbrains.kotlin.resolve.calls.tower.CandidateApplicability
import org.jetbrains.kotlin.types.Variance

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
    val resolveCallInfo =
      CallInfo(
        callSite = resolve, // TODO check generics
        callKind = CallKind.Function,
        name = resolve.name,
        explicitReceiver = null, // TODO()
        argumentList = FirEmptyArgumentList,
        isImplicitInvoke = false,
        typeArguments = emptyList(),
        session = session,
        containingFile = fakeFirFile,
        containingDeclarations = emptyList(), // TODO()
        origin = FirFunctionCallOrigin.Regular, // TODO()
      )

    val candidateFactory =
      CandidateFactory(firBodyResolveTransformer.resolutionContext, resolveCallInfo)

    return mapNotNull { proof ->
        val proofCallInfo =
          CallInfo(
            callSite = proof.declaration,
            callKind =
              when (proof.declaration) {
                is FirFunction -> CallKind.Function
                else -> CallKind.VariableAccess
              },
            name = (proof.declaration.symbol as? FirCallableSymbol)?.name
                ?: Name.identifier("Unsupported"),
            explicitReceiver = null, // TODO()
            argumentList = FirEmptyArgumentList, // TODO()
            isImplicitInvoke = false,
            typeArguments =
              proof.declaration.symbol.typeParameterSymbols.orEmpty().map { typeParameterSymbol ->
                buildTypeProjectionWithVariance {
                  typeRef = buildResolvedTypeRef {
                    this.type = type
                  }
                  variance = typeParameterSymbol.variance
                }
              },
            session = session,
            containingFile = fakeFirFile,
            containingDeclarations = emptyList(), // TODO()
            origin = FirFunctionCallOrigin.Regular, // TODO()
          )

        val candidate: Candidate =
          candidateFactory.createCandidate(
            callInfo = proofCallInfo,
            symbol = proof.declaration.symbol,
            explicitReceiverKind = ExplicitReceiverKind.NO_EXPLICIT_RECEIVER,
            scope = null, // TODO()
            dispatchReceiverValue = null, // TODO()
            givenExtensionReceiverOptions = emptyList(), // TODO()
            objectsByName = false, // TODO()
          )

        val candidateApplicability =
          resultResolutionStageRunner.processCandidate(
            candidate = candidate,
            context = firBodyResolveTransformer.resolutionContext,
            stopOnFirstError = true, // TODO()
          )

        val isCandidateApplicability = candidateApplicability == CandidateApplicability.RESOLVED

        if (!isCandidateApplicability) return@mapNotNull null

        val firFunctionCall = buildFunctionCall {
          typeRef = proof.declaration.coneType?.toFirResolvedTypeRef()!! // TODO()
          argumentList = FirEmptyArgumentList // TODO()
          calleeReference =
            FirNamedReferenceWithCandidate(
              source = proof.declaration.source,
              name = (proof.declaration.symbol as? FirCallableSymbol)?.name
                  ?: Name.identifier("Unsupported"),
              candidate =
                candidate.apply {
                  substitutor = ConeSubstitutor.Empty // TODO()
                  freshVariables = emptyList()
                },
            )
        }

        val (_: FirFunctionCall, isCallCompleted: Boolean) =
          firCallCompleter.completeCall(
            call = firFunctionCall,
            expectedTypeRef = type.toFirResolvedTypeRef(),
            expectedTypeMismatchIsReportedInChecker = true
          )

        if (!isCallCompleted) return@mapNotNull null

        if (candidate.errors.isNotEmpty()) return@mapNotNull null

        candidate
      }
      .toSet()
  }

  private val fakeFirFile: FirFile
    get() = buildFile {
      moduleData = session.moduleData
      origin = ProofKey.origin
      packageDirective = buildPackageDirective { packageFqName = FqName("PROOF_FAKE") }
      name = "PROOF_FAKE"
    }
}
