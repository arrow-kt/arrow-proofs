package arrow.inject.compiler.plugin.fir.resolution.checkers.call

import arrow.inject.compiler.plugin.fir.contextReceivers
import arrow.inject.compiler.plugin.fir.errors.FirMetaErrors
import arrow.inject.compiler.plugin.fir.resolution.resolver.ProofCache
import arrow.inject.compiler.plugin.model.Proof
import arrow.inject.compiler.plugin.model.ProofResolution
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.diagnostics.AbstractSourceElementPositioningStrategy
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.FirAnnotationContainer
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.declarations.FirTypeParameterRefsOwner
import org.jetbrains.kotlin.fir.expressions.FirCall
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.references.FirNamedReference
import org.jetbrains.kotlin.fir.resolvedSymbol
import org.jetbrains.kotlin.fir.scopes.impl.toConeType
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.ConeTypeParameterType
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.renderReadable
import org.jetbrains.kotlin.fir.types.toConeTypeProjection
import org.jetbrains.kotlin.fir.types.type

internal class MissingInductiveDependenciesChecker(
  override val proofCache: ProofCache,
  override val session: FirSession,
) : FirAbstractCallChecker {

  override val allProofs: List<Proof> by lazy { allCollectedProofs }

  override fun report(expression: FirCall, context: CheckerContext, reporter: DiagnosticReporter) {
    val resolvedParameters: Map<ProofResolution?, FirElement> = proofResolutionList(expression)

    resolvedParameters.forEach { (_, element) ->
      element.reportMissingInductiveDependency(expression, reporter, context)
    }
  }

  private fun FirElement.reportMissingInductiveDependency(
    expression: FirCall,
    reporter: DiagnosticReporter,
    context: CheckerContext
  ) {
    if ((this is FirAnnotationContainer) && hasContextualAnnotation) {
      val expressionOwner: FirNamedReference? = (expression as? FirFunctionCall)?.calleeReference
      val returnType = resolutionTargetType()

      val proofResolution: ProofResolution =
        if (returnType is ConeTypeParameterType) {
          val index =
            (expressionOwner?.resolvedSymbol?.fir as? FirTypeParameterRefsOwner)
              ?.typeParameters
              ?.map { it.toConeType() }
              ?.indexOfFirst {
                // TODO comparing `renderReadable` is not a good idea
                it.type.renderReadable() == returnType.renderReadable()
              }
          if (index != null && index != -1) {
            val substitutedType: ConeKotlinType? =
              expression.typeArguments[index].toConeTypeProjection().type

            if (substitutedType != null) {
              resolveProof(substitutedType, mutableListOf())
            } else error("Unexpected type argument index")
          } else {
            error("Unexpected type argument index")
          }
        } else {
          resolveProof(resolutionTargetType(), mutableListOf())
        }

      if (proofResolution.proof != null) {
        reportContextReceiver(proofResolution, expression, reporter, context)
      }
    }
  }

  private fun FirElement.reportContextReceiver(
    proofResolution: ProofResolution,
    expression: FirCall,
    reporter: DiagnosticReporter,
    context: CheckerContext
  ) {
    val contextReceivers = proofResolution.proof?.declaration?.contextReceivers

    contextReceivers?.forEach { contextReceiver ->
      val targetType =
        targetTypeRef(
          proofResolution.targetType as ConeKotlinType,
          contextReceiver.typeRef.coneType
        )

      val contextReceiverProof = resolveProof(targetType.type, mutableListOf())

      val expressionSource: KtSourceElement? = expression.source

      if (contextReceiverProof.proof == null && expressionSource != null) {
        reporter.report(
          FirMetaErrors.UNRESOLVED_GIVEN_CALL_SITE.on(
            expressionSource,
            expression,
            resolutionTargetType(),
            AbstractSourceElementPositioningStrategy.DEFAULT,
          ),
          context,
        )
      }
    }
  }
}
