@file:OptIn(DfaInternals::class)

package arrow.inject.compiler.plugin.fir

import arrow.inject.compiler.plugin.fir.utils.FirUtils
import arrow.inject.compiler.plugin.fir.utils.coneType
import arrow.inject.compiler.plugin.proof.Proof
import java.util.concurrent.atomic.AtomicInteger
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.FirResolvePhase
import org.jetbrains.kotlin.fir.expressions.FirEmptyArgumentList
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirFunctionCallOrigin
import org.jetbrains.kotlin.fir.expressions.FirImplicitInvokeCall
import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccessExpression
import org.jetbrains.kotlin.fir.expressions.buildResolvedArgumentList
import org.jetbrains.kotlin.fir.expressions.builder.buildFunctionCall
import org.jetbrains.kotlin.fir.resolve.ScopeSession
import org.jetbrains.kotlin.fir.resolve.calls.CallInfo
import org.jetbrains.kotlin.fir.resolve.calls.CallKind
import org.jetbrains.kotlin.fir.resolve.calls.Candidate
import org.jetbrains.kotlin.fir.resolve.calls.CandidateFactory
import org.jetbrains.kotlin.fir.resolve.calls.FirNamedReferenceWithCandidate
import org.jetbrains.kotlin.fir.resolve.calls.ResolutionStageRunner
import org.jetbrains.kotlin.fir.resolve.dfa.DfaInternals
import org.jetbrains.kotlin.fir.resolve.inference.FirCallCompleter
import org.jetbrains.kotlin.fir.resolve.substitution.ConeSubstitutor
import org.jetbrains.kotlin.fir.resolve.transformers.body.resolve.FirBodyResolveTransformer
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.types.toFirResolvedTypeRef
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.calls.tasks.ExplicitReceiverKind
import org.jetbrains.kotlin.resolve.calls.tower.CandidateApplicability

class ProofResolutionStageRunner(override val session: FirSession) : FirUtils {

  override val counter: AtomicInteger = AtomicInteger()

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

  fun List<Proof>.matchingCandidates(expression: FirQualifiedAccessExpression): Set<Candidate> {
    val resolveCallInfo =
      CallInfo(
        callSite = expression,
        callKind =
          if (expression is FirFunctionCall) CallKind.Function else CallKind.VariableAccess,
        name = (expression as? FirFunctionCall)?.calleeReference?.name
            ?: Name.identifier("Unsupported"),
        explicitReceiver = null, // TODO()
        argumentList = (expression as? FirFunctionCall)?.argumentList ?: FirEmptyArgumentList,
        isImplicitInvoke = expression is FirImplicitInvokeCall,
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
            typeArguments = emptyList(),
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
            expectedTypeRef = expression.typeRef,
            expectedTypeMismatchIsReportedInChecker = true
          )

        if (!isCallCompleted) return@mapNotNull null

        if (candidate.errors.isNotEmpty()) return@mapNotNull null

        candidate
      }
      .toSet()
  }
}
