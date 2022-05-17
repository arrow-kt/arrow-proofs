@file:OptIn(InternalDiagnosticFactoryMethod::class)

package arrow.inject.compiler.plugin.fir.resolution.checkers.call

import arrow.inject.compiler.plugin.fir.errors.FirMetaErrors
import arrow.inject.compiler.plugin.fir.resolution.resolver.ProofCache
import arrow.inject.compiler.plugin.fir.resolution.resolver.ProofResolutionStageRunner
import arrow.inject.compiler.plugin.model.Proof
import arrow.inject.compiler.plugin.model.ProofResolution
import org.jetbrains.kotlin.diagnostics.AbstractSourceElementPositioningStrategy
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.InternalDiagnosticFactoryMethod
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.declarations.FirValueParameter
import org.jetbrains.kotlin.fir.expressions.FirCall

internal class AmbiguousProofsChecker(
  override val proofCache: ProofCache,
  override val session: FirSession,
) : FirAbstractCallChecker {

  override val proofResolutionStageRunner: ProofResolutionStageRunner by lazy {
    ProofResolutionStageRunner(session)
  }

  override val allProofs: List<Proof> by lazy { allCollectedProofs }

  override fun report(expression: FirCall, context: CheckerContext, reporter: DiagnosticReporter) {
    proofResolutionList(expression).let {
      resolvedParameters: Map<ProofResolution?, FirValueParameter> ->
      resolvedParameters.forEach { (proofResolution, _) ->
        val source = expression.source
        val proof = proofResolution?.proof
        if (proofResolution?.isAmbiguous == true && source != null && proof != null) {
          reporter.report(
            FirMetaErrors.AMBIGUOUS_PROOF_FOR_SUPERTYPE.on(
              source,
              proofResolution.targetType,
              proofResolution.proof,
              proofResolution.ambiguousProofs,
              AbstractSourceElementPositioningStrategy.DEFAULT,
            ),
            context
          )
        }
      }
    }
  }
}
