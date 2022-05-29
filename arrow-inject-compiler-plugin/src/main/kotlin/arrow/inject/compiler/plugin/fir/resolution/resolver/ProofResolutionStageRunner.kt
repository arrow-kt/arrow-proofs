@file:OptIn(
  SymbolInternals::class,
  DfaInternals::class,
  DfaInternals::class,
  PrivateForInline::class
)

package arrow.inject.compiler.plugin.fir.resolution.resolver

import arrow.inject.compiler.plugin.fir.FirAbstractProofComponent
import arrow.inject.compiler.plugin.fir.FirResolutionProof
import arrow.inject.compiler.plugin.fir.ProofKey
import arrow.inject.compiler.plugin.fir.contextReceivers
import arrow.inject.compiler.plugin.model.Proof
import arrow.inject.compiler.plugin.model.ProofAnnotationsFqName
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.PrivateForInline
import org.jetbrains.kotlin.fir.analysis.checkers.typeParameterSymbols
import org.jetbrains.kotlin.fir.builder.buildPackageDirective
import org.jetbrains.kotlin.fir.declarations.FirCallableDeclaration
import org.jetbrains.kotlin.fir.declarations.FirContextReceiver
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirFile
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.FirResolvePhase
import org.jetbrains.kotlin.fir.declarations.builder.buildFile
import org.jetbrains.kotlin.fir.expressions.FirEmptyArgumentList
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirFunctionCallOrigin
import org.jetbrains.kotlin.fir.expressions.buildResolvedArgumentList
import org.jetbrains.kotlin.fir.expressions.builder.buildArgumentList
import org.jetbrains.kotlin.fir.expressions.builder.buildFunctionCall
import org.jetbrains.kotlin.fir.labelName
import org.jetbrains.kotlin.fir.moduleData
import org.jetbrains.kotlin.fir.references.FirResolvedNamedReference
import org.jetbrains.kotlin.fir.references.builder.buildResolvedNamedReference
import org.jetbrains.kotlin.fir.resolve.FirRegularTowerDataContexts
import org.jetbrains.kotlin.fir.resolve.ScopeSession
import org.jetbrains.kotlin.fir.resolve.calls.CallInfo
import org.jetbrains.kotlin.fir.resolve.calls.CallKind
import org.jetbrains.kotlin.fir.resolve.calls.Candidate
import org.jetbrains.kotlin.fir.resolve.calls.CandidateFactory
import org.jetbrains.kotlin.fir.resolve.calls.ContextReceiverValue
import org.jetbrains.kotlin.fir.resolve.calls.ContextReceiverValueForCallable
import org.jetbrains.kotlin.fir.resolve.calls.ContextReceiverValueForClass
import org.jetbrains.kotlin.fir.resolve.calls.FirNamedReferenceWithCandidate
import org.jetbrains.kotlin.fir.resolve.calls.ResolutionStageRunner
import org.jetbrains.kotlin.fir.resolve.dfa.DfaInternals
import org.jetbrains.kotlin.fir.resolve.inference.FirCallCompleter
import org.jetbrains.kotlin.fir.resolve.transformers.body.resolve.FirBodyResolveTransformer
import org.jetbrains.kotlin.fir.scopes.impl.toConeType
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirValueParameterSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.FirTypeProjection
import org.jetbrains.kotlin.fir.types.FirTypeRef
import org.jetbrains.kotlin.fir.types.builder.buildResolvedTypeRef
import org.jetbrains.kotlin.fir.types.builder.buildTypeProjectionWithVariance
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.toFirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.type
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.calls.tasks.ExplicitReceiverKind
import org.jetbrains.kotlin.resolve.calls.tower.CandidateApplicability
import org.jetbrains.kotlin.types.Variance

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
    previousProofs: MutableList<Proof>
  ): ProofResolutionResult {
    val resolveCallInfo = resolveCallInfo()
    return ProofResolutionResult.Candidates(
      mapNotNull { proof ->
          val proofDeclaration: FirDeclaration = proof.declaration
          // val hasCycles = proof.hasCycle(type, previousProofs)
          // if (hasCycles) null
          // else
          when (val callInfoResult = proof.proofCallInfo(proofDeclaration, type)) {
            is CallInfoResult.Info -> {
              val completedCandidate =
                createAndCompleteCandidate(resolveCallInfo, callInfoResult, proof, type)
              completedCandidate
            }
            is CallInfoResult.CyclesFound ->
              return ProofResolutionResult.CyclesFound(callInfoResult.proof)
            is CallInfoResult.FunctionCall -> TODO()
          }
        }
        .toSet()
    )
  }

  private fun List<Proof>.createAndCompleteCandidate(
    resolveCallInfo: CallInfo,
    callInfoResult: CallInfoResult.Info,
    proof: Proof,
    type: ConeKotlinType
  ): Candidate? {
    val previousTowerContext =
      firBodyResolveTransformer.resolutionContext.bodyResolveContext.regularTowerDataContexts
    firBodyResolveTransformer.resolutionContext.bodyResolveContext.regularTowerDataContexts =
      FirRegularTowerDataContexts(
        firBodyResolveTransformer.resolutionContext.bodyResolveContext.towerDataContext
          .addContextReceiverGroup(flatMap { it.buildCallInfoContextReceiverValues(type) })
      )
    val candidateFactory =
      CandidateFactory(firBodyResolveTransformer.resolutionContext, resolveCallInfo)
    val completedCandidate = callInfoResult.completedCandidate(candidateFactory, proof, type)
    firBodyResolveTransformer.resolutionContext.bodyResolveContext.regularTowerDataContexts =
      previousTowerContext

    return completedCandidate
  }

  private fun Proof.hasCycle(type: ConeKotlinType, previousProofs: MutableList<Proof>): Boolean {
    return if (this in previousProofs) true
    else {
      previousProofs.add(this)
      declaration.contextReceivers.any {
        val targetType = targetType(type, it.typeRef.coneType) ?: it.typeRef.coneType
        val receiverProofResolution =
          firResolutionProof.resolveProof(
            ProofAnnotationsFqName.ProviderAnnotation,
            targetType.type,
            previousProofs
          )
        receiverProofResolution.proof == this
      }
    }
  }

  private fun CallInfoResult.Info.completedCandidate(
    candidateFactory: CandidateFactory,
    proof: Proof,
    type: ConeKotlinType,
  ): Candidate? {
    val candidate = candidate(candidateFactory, callInfo, proof, type)
    return when {
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
          contextReceiver.buildContextReceiverCall(type)
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
    type: ConeKotlinType,
  ): Candidate {
    return candidateFactory.createCandidate(
      callInfo = proofCallInfo,
      symbol = proof.declaration.symbol,
      explicitReceiverKind =
        if (proof.declaration.contextReceivers.isEmpty()) ExplicitReceiverKind.NO_EXPLICIT_RECEIVER
        else ExplicitReceiverKind.EXTENSION_RECEIVER,
      scope = null,
      dispatchReceiverValue = null,
      givenExtensionReceiverOptions = proof.buildCallInfoContextReceiverValues(type),
      objectsByName = false,
    )
  }

  private fun Proof.buildCallInfoContextReceiverValues(
    type: ConeKotlinType
  ): List<ContextReceiverValue<*>> =
    declaration.contextReceivers.mapIndexedNotNull { index, receiver ->
      declaration.contextReceiverValue(
        session = session,
        scopeSession = scopeSession,
        type = type,
        receiver = receiver,
        index = index,
      )
    }

  private fun FirDeclaration.contextReceiverValue(
    session: FirSession,
    scopeSession: ScopeSession,
    type: ConeKotlinType,
    receiver: FirContextReceiver,
    index: Int
  ): ContextReceiverValue<*>? {
    val targetType = targetType(type, receiver.typeRef.coneType) ?: receiver.typeRef.coneType

    return when (this) {
      is FirCallableDeclaration ->
        ContextReceiverValueForCallable(
          symbol,
          targetType,
          receiver.labelName,
          session,
          scopeSession,
          contextReceiverNumber = index,
        )
      is FirRegularClass ->
        ContextReceiverValueForClass(
          symbol,
          targetType,
          receiver.labelName,
          session,
          scopeSession,
          contextReceiverNumber = index,
        )
      else -> null
    }
  }

  private fun Proof.proofCallInfo(
    proofDeclaration: FirDeclaration,
    type: ConeKotlinType,
  ): CallInfoResult {
    val explicitReceiverResult = buildCallInfoExplicitReceiver(type)
    return CallInfoResult.Info(
      CallInfo(
        callSite = declaration,
        callKind = buildCallKind(),
        name = declarationName,
        explicitReceiver = explicitReceiverResult,
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

  private fun Proof.buildCallInfoExplicitReceiver(type: ConeKotlinType): FirFunctionCall? =
    declaration.contextReceivers
      .mapNotNull { contextReceiver ->

        //        println()
        //        val proofResolutionResult = // TODO
        //          firResolutionProof.resolveProof(
        //            contextFqName = ProviderAnnotation,
        //            type = targetType,
        //          )

        //        when (val result = proofResolutionResult.result) {
        //          is ProofResolutionResult.Candidates -> {
        //            if (proofResolutionResult.proof != null) null
        //            else contextReceiver.buildContextReceiverCall()
        //          }
        //          is ProofResolutionResult.CyclesFound -> return
        // CallInfoResult.CyclesFound(result.proof)
        //          else -> null
        //        }
        contextReceiver.buildContextReceiverCall(type)
      }
      .firstOrNull()

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

  private fun FirTypeRef.buildContextReceiversTypeProjections(
    type: ConeKotlinType
  ): List<FirTypeProjection> =
    coneType.typeArguments.map {
      buildTypeProjectionWithVariance {
        val typeArgType = it.type
        if (typeArgType != null) {
          val targetTypeRefArg = targetTypeRef(type, typeArgType)
          typeRef = targetTypeRefArg
          variance = Variance.OUT_VARIANCE
        }
      }
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

  private fun FirContextReceiver.buildContextReceiverCall(type: ConeKotlinType): FirFunctionCall {
    val targetTypeRef = targetTypeRef(type, typeRef.coneType)
    return buildFunctionCall {
      typeRef = targetTypeRef
      argumentList = buildResolvedArgumentList(LinkedHashMap())
      typeArguments += targetTypeRef.buildContextReceiversTypeProjections(type)
      calleeReference = buildSyntheticResolveCalleeReference()
    }
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
