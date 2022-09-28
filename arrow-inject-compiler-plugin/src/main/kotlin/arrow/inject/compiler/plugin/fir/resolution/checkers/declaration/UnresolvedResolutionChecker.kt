package arrow.inject.compiler.plugin.fir.resolution.checkers.declaration

import arrow.inject.compiler.plugin.fir.collectors.ExternalProofCollector
import arrow.inject.compiler.plugin.fir.collectors.LocalProofCollectors
import arrow.inject.compiler.plugin.fir.errors.FirMetaErrors
import arrow.inject.compiler.plugin.fir.resolution.checkers.resolutionTargetType
import arrow.inject.compiler.plugin.fir.resolution.resolver.ProofCache
import arrow.inject.compiler.plugin.model.Proof
import arrow.inject.compiler.plugin.model.ProofResolution
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.diagnostics.AbstractSourceElementPositioningStrategy
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.caches.FirLazyValue
import org.jetbrains.kotlin.fir.caches.firCachesFactory
import org.jetbrains.kotlin.fir.declarations.FirCallableDeclaration
import org.jetbrains.kotlin.fir.psi
import org.jetbrains.kotlin.toKtPsiSourceElement

internal class UnresolvedResolutionChecker(
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
    if (declaration.hasContextResolutionAnnotation)
      proofResolutionList(declaration).let { resolvedParameters: Map<ProofResolution?, FirElement>
        ->
        resolvedParameters.forEach { (proofResolution, contextReceiver) ->
          val expressionSource: KtSourceElement? = contextReceiver.psi?.toKtPsiSourceElement()

          if (proofResolution?.proof == null && expressionSource != null) {
            reporter.report(
              FirMetaErrors.UNRESOLVED_CONTEXT_RESOLUTION.on(
                expressionSource,
                contextReceiver,
                contextReceiver.resolutionTargetType(),
                AbstractSourceElementPositioningStrategy.DEFAULT,
              ),
              context,
            )
          }
        }
      }
  }
}
