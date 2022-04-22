@file:OptIn(
  SymbolInternals::class,
  DfaInternals::class,
  PrivateForInline::class,
  SessionConfiguration::class,
)

package arrow.inject.compiler.plugin.fir

import arrow.inject.compiler.plugin.fir.utils.FirUtils
import arrow.inject.compiler.plugin.fir.utils.Predicate
import arrow.inject.compiler.plugin.fir.utils.coneType
import arrow.inject.compiler.plugin.fir.utils.isContextAnnotation
import arrow.inject.compiler.plugin.proof.Proof
import arrow.inject.compiler.plugin.proof.ProofResolution
import java.util.concurrent.atomic.AtomicInteger
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.FirCallResolver
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.FirSessionComponent
import org.jetbrains.kotlin.fir.OverloadCandidate
import org.jetbrains.kotlin.fir.PrivateForInline
import org.jetbrains.kotlin.fir.SessionConfiguration
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.ExpressionCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirCallChecker
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension
import org.jetbrains.kotlin.fir.builder.buildPackageDirective
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.FirResolvePhase
import org.jetbrains.kotlin.fir.declarations.FirValueParameter
import org.jetbrains.kotlin.fir.declarations.builder.buildFile
import org.jetbrains.kotlin.fir.expressions.FirCall
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirResolvable
import org.jetbrains.kotlin.fir.extensions.FirPredicateBasedProvider
import org.jetbrains.kotlin.fir.extensions.FirPredicateBasedProviderImpl
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.moduleData
import org.jetbrains.kotlin.fir.resolve.ScopeSession
import org.jetbrains.kotlin.fir.resolve.dfa.DfaInternals
import org.jetbrains.kotlin.fir.resolve.fqName
import org.jetbrains.kotlin.fir.resolve.transformers.body.resolve.FirBodyResolveTransformer
import org.jetbrains.kotlin.fir.resolvedSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.isNothing
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

class ProofResolutionCallCheckerExtension(
  session: FirSession,
) : FirAdditionalCheckersExtension(session), FirUtils {

  override val expressionCheckers: ExpressionCheckers =
    object : ExpressionCheckers() {
      override val callCheckers: Set<FirCallChecker> =
        setOf(
          object : FirCallChecker() {
            override fun check(
              expression: FirCall,
              context: CheckerContext,
              reporter: DiagnosticReporter
            ) {
              reportUnresolvedGivenCallSite(expression, context, reporter)
            }
          }
        )
    }

  override val counter: AtomicInteger = AtomicInteger(0)

  private fun reportUnresolvedGivenCallSite(
    expression: FirCall,
    context: CheckerContext,
    reporter: DiagnosticReporter
  ): Unit =
    unresolvedGivenCallSite(expression).let { values ->
      //    values.forEach { (resolution, v) ->
      //      if (resolution?.ambiguousProofs?.isNotEmpty() == true &&
      //        resolution.ambiguousProofs.size > 1 &&
      //        resolution.givenProof != null
      //      ) {
      //        trace.report(
      //          AmbiguousProofForSupertype.on(
      //            element,
      //            resolution.targetType,
      //            resolution.givenProof,
      //            resolution.ambiguousProofs
      //          )
      //        )
      //      }
      //      if (resolution?.givenProof == null) {
      //        reportMissingInductiveDependencies(v, trace, element, call)
      //        trace.report(UnresolvedGivenCallSite.on(element, call, v.type))
      //      }
      //    }
    }

  private fun unresolvedGivenCallSite(call: FirCall): Map<ProofResolution?, FirValueParameter> {
    val originalFunction: FirFunction? =
      ((call as? FirResolvable)?.calleeReference?.resolvedSymbol as? FirFunctionSymbol<*>)?.fir

    return if (originalFunction?.isCompileTimeAnnotated == true) {
      val unresolvedValueParameters: List<FirValueParameter> =
        originalFunction.valueParameters.filter {
          val defaultValue: FirFunctionCall? = (it.defaultValue as? FirFunctionCall)
          defaultValue?.calleeReference?.resolvedSymbol == resolve.symbol
        }
      resolvedValueParametersMap(unresolvedValueParameters, call)
    } else {
      emptyMap()
    }
  }

  private fun resolvedValueParametersMap(
    unresolvedValueParameters: List<FirValueParameter>,
    call: FirCall
  ) =
    unresolvedValueParameters
      .mapNotNull { valueParameter: FirValueParameter ->
        val contextFqName: FqName? =
          valueParameter
            .annotations
            .firstOrNull { it.isContextAnnotation(session) }
            ?.fqName(session)

        val defaultValue = valueParameter.defaultValue

        if (contextFqName != null && defaultValue is FirQualifiedAccessExpression) {
          val proofResolution =
            resolveProof(contextFqName, defaultValue, valueParameter.returnTypeRef.coneType)
          if (proofResolution.proof == null) null to valueParameter
          else proofResolution to valueParameter
        } else {
          null
        }
      }
      .toMap()

  private fun resolveProof(
    contextFqName: FqName,
    expression: FirQualifiedAccessExpression,
    type: ConeKotlinType
  ): ProofResolution =
    proofCandidate(
      candidates = candidates(contextFqName, expression, type),
      expression = expression,
      type = type,
    )

  private fun candidates(
    contextFqName: FqName,
    expression: FirQualifiedAccessExpression,
    type: ConeKotlinType
  ): List<OverloadCandidate> =
    collectLocalProofs()
      .filter { contextFqName in it.contexts(session) }
      .matchingCandidates(expression, type)

  private fun proofCandidate(
    candidates: List<OverloadCandidate>,
    expression: FirQualifiedAccessExpression,
    type: ConeKotlinType,
  ): ProofResolution {
    val candidate: OverloadCandidate? = candidates.firstOrNull { it.isInBestCandidates }
    return ProofResolution(
      proof = candidate?.let { Proof.Implication(it.candidate.symbol.fir) },
      targetType = type,
      ambiguousProofs =
        (candidates - candidate).mapNotNull {
          if (it != null) Proof.Implication(it.candidate.symbol.fir) else null
        }
    )
  }

  private fun collectLocalProofs(): List<Proof> =
    session.predicateBasedProvider.getSymbolsByPredicate(Predicate.META_CONTEXT_PREDICATE)
      .mapNotNull { firBasedSymbol ->
        firBasedSymbol.fir.coneType?.let { type -> Proof.Implication(firBasedSymbol.fir) }
      }

  fun List<Proof>.matchingCandidates(
    expression: FirQualifiedAccessExpression,
    type: ConeKotlinType
  ): List<OverloadCandidate> {
    val candidates: List<OverloadCandidate> =
      if (type.isNothing) {
        emptyList()
      } else {
        val callResolver = firCallResolver
        callResolver.collectAllCandidates(
          qualifiedAccess = expression,
          name = Name.identifier("Proof Resolution"),
          containingDeclarations = this.map { it.declaration },
        )
      }
    return candidates
  }

  private val scopeSession = ScopeSession()

  private val firBodyResolveTransformer: FirBodyResolveTransformer
    get() =
      FirBodyResolveTransformer(
        session = session,
        phase = FirResolvePhase.BODY_RESOLVE,
        implicitTypeOnly = false,
        scopeSession = scopeSession,
      )

  private val firCallResolver: FirCallResolver
    get() =
      firBodyResolveTransformer.components
        .apply {
          context.file = buildFile {
            moduleData = session.moduleData
            origin = Key.origin
            packageDirective = buildPackageDirective { packageFqName = FqName("PROOF_FAKE") }
            name = "PROOF_FAKE"
          }
        }
        .callResolver
}
