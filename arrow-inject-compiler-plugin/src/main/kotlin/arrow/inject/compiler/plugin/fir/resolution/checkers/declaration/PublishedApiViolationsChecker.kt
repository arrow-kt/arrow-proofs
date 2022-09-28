package arrow.inject.compiler.plugin.fir.resolution.checkers.declaration

import arrow.inject.compiler.plugin.fir.collectors.ExternalProofCollector
import arrow.inject.compiler.plugin.fir.collectors.LocalProofCollectors
import arrow.inject.compiler.plugin.fir.errors.FirMetaErrors
import arrow.inject.compiler.plugin.fir.resolution.resolver.ProofCache
import arrow.inject.compiler.plugin.model.Proof
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.diagnostics.AbstractSourceElementPositioningStrategy
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.caches.FirLazyValue
import org.jetbrains.kotlin.fir.caches.firCachesFactory
import org.jetbrains.kotlin.fir.declarations.FirCallableDeclaration
import org.jetbrains.kotlin.fir.declarations.utils.visibility
import org.jetbrains.kotlin.fir.resolve.transformers.publishedApiEffectiveVisibility

internal class PublishedApiViolationsChecker(
  override val proofCache: ProofCache,
  override val session: FirSession,
) : FirAbstractDeclarationChecker {

  override val allProofs: List<Proof> by lazy { allCollectedProofs }

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
    declaration.takeProofIfViolatingPublishingApiRule()?.let {
      val source: KtSourceElement? = it.declaration.source
      if (source != null) {
        reporter.report(
          FirMetaErrors.PUBLISHED_INTERNAL_ORPHAN.on(
            source,
            it,
            AbstractSourceElementPositioningStrategy.DEFAULT
          ),
          context
        )
      }
    }
  }

  private fun FirCallableDeclaration.takeProofIfViolatingPublishingApiRule(): Proof? =
    allFirLazyProofs.getValue(Unit).firstOrNull {
      it.declaration.symbol == this.symbol &&
        this.visibility == Visibilities.Internal &&
        this.publishedApiEffectiveVisibility?.publicApi == true
    }
}
