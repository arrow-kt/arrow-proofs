@file:OptIn(InternalDiagnosticFactoryMethod::class, InternalDiagnosticFactoryMethod::class)

package arrow.inject.compiler.plugin.fir.resolution.checkers.declaration

import arrow.inject.compiler.plugin.fir.errors.FirMetaErrors
import arrow.inject.compiler.plugin.model.Proof
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.diagnostics.AbstractSourceElementPositioningStrategy
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.InternalDiagnosticFactoryMethod
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.declarations.FirCallableDeclaration
import org.jetbrains.kotlin.fir.declarations.utils.visibility
import org.jetbrains.kotlin.fir.resolve.transformers.publishedApiEffectiveVisibility

internal class PublishedApiViolationsChecker(override val session: FirSession) :
  FirAbstractDeclarationChecker {

  override val allProofs: List<Proof> by lazy { allCollectedProofs }

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
    takeIf { it.metaContextAnnotations.isNotEmpty() }
      ?.let {
        allProofs.firstOrNull {
          it.declaration.symbol == this.symbol &&
            this.visibility == Visibilities.Internal &&
            this.publishedApiEffectiveVisibility?.publicApi == true
        }
      }
}
