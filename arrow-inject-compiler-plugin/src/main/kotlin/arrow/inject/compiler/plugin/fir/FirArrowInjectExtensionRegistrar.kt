package arrow.inject.compiler.plugin.fir

import arrow.inject.compiler.plugin.fir.codegen.ContextResolutionCodegen
import arrow.inject.compiler.plugin.fir.resolution.ProofResolutionCheckerExtension
import arrow.inject.compiler.plugin.fir.resolution.checkers.call.AmbiguousProofsChecker
import arrow.inject.compiler.plugin.fir.resolution.checkers.call.CyclesDetectionChecker
import arrow.inject.compiler.plugin.fir.resolution.checkers.call.MissingInductiveDependenciesChecker
import arrow.inject.compiler.plugin.fir.resolution.checkers.call.UnresolvedCallSiteChecker
import arrow.inject.compiler.plugin.fir.resolution.checkers.declaration.AmbiguousDeclarationsProofsChecker
import arrow.inject.compiler.plugin.fir.resolution.checkers.declaration.DeclarationCyclesDetectionChecker
import arrow.inject.compiler.plugin.fir.resolution.checkers.declaration.MissingInductiveDeclarationsChecker
import arrow.inject.compiler.plugin.fir.resolution.checkers.declaration.OwnershipViolationsChecker
import arrow.inject.compiler.plugin.fir.resolution.checkers.declaration.PublishedApiViolationsChecker
import arrow.inject.compiler.plugin.fir.resolution.checkers.declaration.UnresolvedResolutionChecker
import arrow.inject.compiler.plugin.fir.resolution.checkers.type.ContextReceiverProofTypeChecker
import arrow.inject.compiler.plugin.fir.resolution.contexts.ContextProvidersResolutionExtension
import arrow.inject.compiler.plugin.fir.resolution.contexts.ContextResolutionImplicits
import arrow.inject.compiler.plugin.fir.resolution.resolver.ProofCache
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

public class FirArrowInjectExtensionRegistrar(
  private val proofCache: ProofCache,
) : FirExtensionRegistrar() {

  override fun ExtensionRegistrarContext.configurePlugin() {
    +{ session: FirSession -> ContextResolutionCodegen(proofCache, session) }
    +{ session: FirSession -> ContextProvidersResolutionExtension(proofCache, session) }
    +{ session: FirSession -> ContextResolutionImplicits(proofCache, session) }
    +{ session: FirSession ->
      ProofResolutionCheckerExtension(
        session = session,
        declarationCheckers =
          listOf(
            AmbiguousDeclarationsProofsChecker(proofCache, session),
            DeclarationCyclesDetectionChecker(proofCache, session),
            MissingInductiveDeclarationsChecker(proofCache, session),
            OwnershipViolationsChecker(proofCache, session),
            PublishedApiViolationsChecker(proofCache, session),
            UnresolvedResolutionChecker(proofCache, session),
          ),
        callCheckers =
          listOf(
            AmbiguousProofsChecker(proofCache, session),
            CyclesDetectionChecker(proofCache, session),
            MissingInductiveDependenciesChecker(proofCache, session),
            UnresolvedCallSiteChecker(proofCache, session),
          ),
        typeCheckers = listOf(ContextReceiverProofTypeChecker())
      )
    }
  }
}
