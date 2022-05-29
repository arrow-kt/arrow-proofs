@file:OptIn(SymbolInternals::class, DfaInternals::class, DfaInternals::class)

package arrow.inject.compiler.plugin.fir.resolution.resolver

import arrow.inject.compiler.plugin.fir.FirAbstractProofComponent
import arrow.inject.compiler.plugin.fir.FirResolutionProof
import arrow.inject.compiler.plugin.fir.ProofKey
import arrow.inject.compiler.plugin.fir.contextReceiverValue
import arrow.inject.compiler.plugin.fir.contextReceivers
import arrow.inject.compiler.plugin.model.Proof
import arrow.inject.compiler.plugin.model.ProofAnnotationsFqName.ProviderAnnotation
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.typeParameterSymbols
import org.jetbrains.kotlin.fir.builder.buildPackageDirective
import org.jetbrains.kotlin.fir.declarations.FirContextReceiver
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
import org.jetbrains.kotlin.fir.references.FirResolvedNamedReference
import org.jetbrains.kotlin.fir.references.builder.buildResolvedNamedReference
import org.jetbrains.kotlin.fir.resolve.ScopeSession
import org.jetbrains.kotlin.fir.resolve.calls.CallInfo
import org.jetbrains.kotlin.fir.resolve.calls.CallKind
import org.jetbrains.kotlin.fir.resolve.calls.Candidate
import org.jetbrains.kotlin.fir.resolve.calls.CandidateFactory
import org.jetbrains.kotlin.fir.resolve.calls.ContextReceiverValue
import org.jetbrains.kotlin.fir.resolve.calls.FirNamedReferenceWithCandidate
import org.jetbrains.kotlin.fir.resolve.calls.ResolutionStageRunner
import org.jetbrains.kotlin.fir.resolve.dfa.DfaInternals
import org.jetbrains.kotlin.fir.resolve.firClassLike
import org.jetbrains.kotlin.fir.resolve.inference.FirCallCompleter
import org.jetbrains.kotlin.fir.resolve.transformers.body.resolve.FirBodyResolveTransformer
import org.jetbrains.kotlin.fir.scopes.impl.toConeType
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirValueParameterSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.FirTypeProjection
import org.jetbrains.kotlin.fir.types.builder.buildResolvedTypeRef
import org.jetbrains.kotlin.fir.types.builder.buildTypeProjectionWithVariance
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.toFirResolvedTypeRef
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.calls.tasks.ExplicitReceiverKind
import org.jetbrains.kotlin.resolve.calls.tower.CandidateApplicability

internal class ProofResolutionStageRunner(
  override val session: FirSession,
  private val firResolutionProof: FirResolutionProof,
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

  fun List<Proof>.matchingCandidates(
    type: ConeKotlinType,
    currentType: ConeKotlinType?,
  ): ProofResolutionResult {
    val resolveCallInfo = resolveCallInfo()
    forEach {
      val receiverGroup = it.buildCallInfoContextReceiverValues()
      firBodyResolveTransformer.resolutionContext.bodyResolveContext.towerDataContext
        .addContextReceiverGroup(receiverGroup)
    }
    val candidateFactory =
      CandidateFactory(firBodyResolveTransformer.resolutionContext, resolveCallInfo)

    return ProofResolutionResult.Candidates(
      mapNotNull { proof ->
          val proofDeclaration: FirDeclaration = proof.declaration
          when (val callInfoResult = proof.proofCallInfo(proofDeclaration, type, currentType)) {
            is CallInfoResult.Info ->
              callInfoResult.completedCandidate(candidateFactory, proof, type, currentType)
            is CallInfoResult.CyclesFound -> return ProofResolutionResult.CyclesFound(proof)
            is CallInfoResult.FunctionCall -> TODO()
          }
        }
        .toSet()
    )
  }

  private fun CallInfoResult.Info.completedCandidate(
    candidateFactory: CandidateFactory,
    proof: Proof,
    type: ConeKotlinType,
    currentType: ConeKotlinType?
  ): Candidate? {
    val candidate = candidate(candidateFactory, callInfo, proof)
    return when {
      type == currentType -> candidate
      candidate.isApplicable -> candidate.createAndCompleteCall(proof, type)
      else -> null
    }
  }

  private val Candidate.isApplicable: Boolean
    get() = applicability() == CandidateApplicability.RESOLVED

  private fun Candidate.createAndCompleteCall(proof: Proof, type: ConeKotlinType): Candidate? {
    val firFunctionCall = proof.firFunctionCall(type, this)
    val (_: FirFunctionCall, isCallCompleted: Boolean) =
      firCallCompleter.completeCall(
        call = firFunctionCall,
        expectedTypeRef = type.toFirResolvedTypeRef(),
        expectedTypeMismatchIsReportedInChecker = false
      )
    val isValidCandidate = errors.isEmpty()
    return if (isCallCompleted && isValidCandidate) this else null
  }

  private fun Proof.firFunctionCall(type: ConeKotlinType, candidate: Candidate): FirFunctionCall =
    buildFunctionCall {
      contextReceiverArguments +=
        declaration.contextReceivers.map { contextReceiver ->
          buildFunctionCall {
            this.typeRef = contextReceiver.typeRef
            this.calleeReference = buildResolvedNamedReference {
              contextReceiver.labelNameFromTypeRef?.let { this.name = it }
              contextReceiver.typeRef.firClassLike(session)?.symbol?.let {
                this.resolvedSymbol = it
              }
            }
          }
        }
      argumentList = buildResolvedArgumentList(LinkedHashMap())
      typeArguments += buildFirCallTypeArguments(type)
      calleeReference = buildFirCallCalleeReference(candidate)
    }

  private fun Proof.buildFirCallCalleeReference(candidate: Candidate) =
    FirNamedReferenceWithCandidate(
      source = declaration.source,
      name = (declaration.symbol as? FirCallableSymbol)?.name ?: Name.identifier("Unsupported"),
      candidate = candidate,
    )

  private fun Proof.buildFirCallTypeArguments(type: ConeKotlinType) =
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

  private fun Candidate.applicability(): CandidateApplicability =
    resultResolutionStageRunner.processCandidate(
      candidate = this,
      context = firBodyResolveTransformer.resolutionContext,
      stopOnFirstError = true,
    )

  private fun candidate(
    candidateFactory: CandidateFactory,
    proofCallInfo: CallInfo,
    proof: Proof,
  ): Candidate {
    return candidateFactory.createCandidate(
      callInfo = proofCallInfo,
      symbol = proof.declaration.symbol,
      explicitReceiverKind = ExplicitReceiverKind.NO_EXPLICIT_RECEIVER,
      scope = null,
      dispatchReceiverValue = null,
      givenExtensionReceiverOptions = emptyList() /*proof.buildCallInfoContextReceiverValues()*/,
      objectsByName = false,
    )
  }

  private fun Proof.buildCallInfoContextReceiverValues(): List<ContextReceiverValue<*>> =
    declaration.contextReceivers.mapIndexedNotNull { index, receiver ->
      declaration.contextReceiverValue(
        session = session,
        scopeSession = scopeSession,
        receiver = receiver,
        index = index,
      )
    }

  private fun Proof.proofCallInfo(
    proofDeclaration: FirDeclaration,
    type: ConeKotlinType,
    currentType: ConeKotlinType?,
  ): CallInfoResult {
    val explicitReceiverResult = buildCallInfoExplicitReceiver(type, currentType)
    return when (explicitReceiverResult) {
      is CallInfoResult.CyclesFound -> explicitReceiverResult
      is CallInfoResult.Info -> explicitReceiverResult
      is CallInfoResult.FunctionCall ->
        CallInfoResult.Info(
          CallInfo(
            callSite = declaration,
            callKind = buildCallKind(),
            name = declarationName,
            explicitReceiver = explicitReceiverResult.call,
            argumentList = proofDeclaration.buildCallInfoArgumentList(type),
            isImplicitInvoke = false,
            typeArguments = buildCallInfoTypeArguments(type),
            session = session,
            containingFile = fakeFirFile,
            containingDeclarations = emptyList(),
            origin = FirFunctionCallOrigin.Regular,
          )
        )
      null ->
        CallInfoResult.Info(
          CallInfo(
            callSite = declaration,
            callKind = buildCallKind(),
            name = declarationName,
            explicitReceiver = null,
            argumentList = proofDeclaration.buildCallInfoArgumentList(type),
            isImplicitInvoke = false,
            typeArguments = buildCallInfoTypeArguments(type),
            session = session,
            containingFile = fakeFirFile,
            containingDeclarations = emptyList(),
            origin = FirFunctionCallOrigin.Regular,
          )
        )
    }
  }

  private fun Proof.buildCallInfoTypeArguments(type: ConeKotlinType) =
    declaration.symbol.typeParameterSymbols.orEmpty().map { typeParameterSymbol ->
      buildTypeProjectionWithVariance {
        val targetTypeRef = targetTypeRef(type, typeParameterSymbol.toConeType())
        typeRef = buildResolvedTypeRef { this.type = targetTypeRef.type }
        variance = typeParameterSymbol.variance
      }
    }

  private fun FirDeclaration.buildCallInfoArgumentList(type: ConeKotlinType) =
    if (this is FirFunction) {
      buildArgumentList { arguments += buildValueArgumentCalls(type) }
    } else FirEmptyArgumentList

  private fun Proof.buildCallInfoExplicitReceiver(
    type: ConeKotlinType,
    currentType: ConeKotlinType?
  ): CallInfoResult? =
    declaration.contextReceivers
      .mapNotNull { contextReceiver ->
        when (contextReceiver.typeRef.coneType) {
          currentType -> return CallInfoResult.CyclesFound
          else -> {
            val proofResolutionResult =
              firResolutionProof.resolveProof(
                contextFqName = ProviderAnnotation,
                type = contextReceiver.typeRef.coneType,
                currentType = type
              )
            if (proofResolutionResult.proof != null) null
            else contextReceiver.buildContextReceiverCall()
          }
        //        type != contextReceiver.typeRef.coneType -> null
        //        else -> null
        }
      }
      .firstOrNull()
      ?.let { CallInfoResult.FunctionCall(it) }

  private fun FirFunction.buildValueArgumentCalls(type: ConeKotlinType): List<FirFunctionCall> =
    symbol.valueParameterSymbols.map { valueParameter ->
      val targetTypeRef = targetTypeRef(type, valueParameter.resolvedReturnTypeRef.type)
      buildFunctionCall {
        typeRef = targetTypeRef
        argumentList = buildResolvedArgumentList(LinkedHashMap())
        typeArguments += valueParameter.buildValueArgumentTypeProjections(type)
        calleeReference = buildSyntheticResolveCalleeReference()
      }
    }

  private fun buildSyntheticResolveCalleeReference(): FirResolvedNamedReference =
    buildResolvedNamedReference {
      name = resolve.name
      resolvedSymbol = resolve.symbol
    }

  private fun FirValueParameterSymbol.buildValueArgumentTypeProjections(
    type: ConeKotlinType
  ): List<FirTypeProjection> =
    typeParameterSymbols.map {
      buildTypeProjectionWithVariance {
        val targetTypeRefArg = targetTypeRef(type, it.toConeType())
        typeRef = targetTypeRefArg
        variance = it.variance
      }
    }

  private fun FirContextReceiver.buildContextReceiverCall(): FirFunctionCall? {
    val symbol = this@buildContextReceiverCall.typeRef.firClassLike(session)?.symbol
    val label = labelNameFromTypeRef
    return if (symbol != null && label != null) {
      buildFunctionCall {
        this.typeRef = this@buildContextReceiverCall.typeRef
        this.calleeReference = buildResolvedNamedReference {
          this.name = label
          this.resolvedSymbol = symbol
        }
      }
    } else null
  }

  private fun Proof.buildCallKind() =
    when (declaration) {
      is FirFunction -> CallKind.Function
      else -> CallKind.VariableAccess
    }

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
