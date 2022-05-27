@file:OptIn(InternalDiagnosticFactoryMethod::class)

package arrow.inject.compiler.plugin.fir.resolution.checkers.call

import arrow.inject.compiler.plugin.fir.errors.FirMetaErrors
import arrow.inject.compiler.plugin.fir.resolution.resolver.ProofCache
import arrow.inject.compiler.plugin.model.Proof
import arrow.inject.compiler.plugin.model.ProofResolution
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.diagnostics.AbstractSourceElementPositioningStrategy
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.InternalDiagnosticFactoryMethod
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.expressions.FirCall
import org.jetbrains.kotlin.fir.psi
import org.jetbrains.kotlin.toKtPsiSourceElement

internal class UnresolvedCallSiteChecker(
  override val proofCache: ProofCache,
  override val session: FirSession,
) : FirAbstractCallChecker {

  override val allProofs: List<Proof> by lazy { allCollectedProofs }

  override fun report(expression: FirCall, context: CheckerContext, reporter: DiagnosticReporter) {
    proofResolutionList(expression).let {
      resolvedParameters: Map<ProofResolution?, FirElement> ->
      resolvedParameters.forEach { (proofResolution, valueParameter) ->
        val expressionSource: KtSourceElement? = expression.psi?.toKtPsiSourceElement()

        if (proofResolution?.proof == null && expressionSource != null) {
          reporter.report(
            FirMetaErrors.UNRESOLVED_GIVEN_CALL_SITE.on(
              expressionSource,
              expression,
              valueParameter.resolutionTargetType(),
              AbstractSourceElementPositioningStrategy.DEFAULT,
            ),
            context,
          )
        }
      }
    }
  }
}
