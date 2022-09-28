package arrow.inject.compiler.plugin.fir.resolution.checkers.declaration

import arrow.inject.compiler.plugin.fir.collectors.ExternalProofCollector
import arrow.inject.compiler.plugin.fir.collectors.LocalProofCollectors
import arrow.inject.compiler.plugin.fir.errors.FirMetaErrors
import arrow.inject.compiler.plugin.fir.resolution.resolver.ProofCache
import arrow.inject.compiler.plugin.model.Proof
import arrow.inject.compiler.plugin.model.ProofResolution
import org.jetbrains.kotlin.diagnostics.AbstractSourceElementPositioningStrategy
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.caches.FirLazyValue
import org.jetbrains.kotlin.fir.caches.firCachesFactory
import org.jetbrains.kotlin.fir.declarations.FirCallableDeclaration
import org.jetbrains.kotlin.fir.expressions.FirCall

internal class AmbiguousDeclarationsProofsChecker(
  override val proofCache: ProofCache,
  override val session: FirSession,
) : FirAbstractDeclarationChecker {

  override val allFirLazyProofs: FirLazyValue<List<Proof>, Unit> =
    session.firCachesFactory.createLazyValue {
      LocalProofCollectors(session).collectLocalProofs() +
        ExternalProofCollector(session).collectExternalProofs()
    }

  override fun report(
    declaration: FirCallableDeclaration,
    context: CheckerContext,
    reporter: DiagnosticReporter
  ) {
    if (declaration.hasContextResolutionAnnotation) {
      proofResolutionList(declaration).let { resolvedParameters: Map<ProofResolution?, FirElement> ->
        resolvedParameters.forEach { (proofResolution, element) ->
          val source = element.source
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
}
