package arrow.inject.compiler.plugin.fir

import arrow.inject.compiler.plugin.fir.resolution.ProofResolutionCheckerExtension
import arrow.inject.compiler.plugin.fir.resolution.checkers.call.AmbiguousProofsChecker
import arrow.inject.compiler.plugin.fir.resolution.checkers.call.MissingInductiveDependenciesChecker
import arrow.inject.compiler.plugin.fir.resolution.checkers.call.UnresolvedCallSiteChecker
import arrow.inject.compiler.plugin.fir.resolution.checkers.declaration.CyclesDetectionChecker
import arrow.inject.compiler.plugin.fir.resolution.checkers.declaration.OwnershipViolationsChecker
import arrow.inject.compiler.plugin.fir.resolution.checkers.declaration.PublishedApiViolationsChecker
import arrow.inject.compiler.plugin.fir.resolution.resolver.ProofCache
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

public class FirArrowInjectExtensionRegistrar(
  private val proofCache: ProofCache,
) : FirExtensionRegistrar() {

  override fun ExtensionRegistrarContext.configurePlugin() {
    +{ session: FirSession ->
      ProofResolutionCheckerExtension(
        session = session,
        declarationCheckers =
          listOf(
            CyclesDetectionChecker(proofCache, session),
            OwnershipViolationsChecker(proofCache, session),
            PublishedApiViolationsChecker(proofCache, session),
          ),
        callCheckers =
          listOf(
            AmbiguousProofsChecker(proofCache, session),
            // TODO: remove -> CyclesDetectionChecker(proofCache, session),
            MissingInductiveDependenciesChecker(proofCache, session),
            UnresolvedCallSiteChecker(proofCache, session),
          ),
      )
    }
  }
}
