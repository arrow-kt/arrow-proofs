package arrow.inject.compiler.plugin.fir

import arrow.inject.compiler.plugin.fir.codegen.ResolvedFunctionGenerationExtension
import arrow.inject.compiler.plugin.fir.resolution.ProofResolutionCheckerExtension
import arrow.inject.compiler.plugin.fir.resolution.checkers.call.AmbiguousProofsChecker
import arrow.inject.compiler.plugin.fir.resolution.checkers.call.CyclesDetectionChecker
import arrow.inject.compiler.plugin.fir.resolution.checkers.call.MissingInductiveDependenciesChecker
import arrow.inject.compiler.plugin.fir.resolution.checkers.call.UnresolvedCallSiteChecker
import arrow.inject.compiler.plugin.fir.resolution.checkers.declaration.OwnershipViolationsChecker
import arrow.inject.compiler.plugin.fir.resolution.resolver.ProofCache
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

class FirArrowInjectExtensionRegistrar(
  private val proofCache: ProofCache,
) : FirExtensionRegistrar() {

  override fun ExtensionRegistrarContext.configurePlugin() {
    +{ session: FirSession -> ResolvedFunctionGenerationExtension(session) }
    +{ session: FirSession ->
      ProofResolutionCheckerExtension(
        session = session,
        declarationCheckers =
          listOf(
            OwnershipViolationsChecker(session),
          ),
        callCheckers =
          listOf(
            AmbiguousProofsChecker(proofCache, session),
            CyclesDetectionChecker(proofCache, session),
            MissingInductiveDependenciesChecker(proofCache, session),
            UnresolvedCallSiteChecker(proofCache, session),
          ),
      )
    }
  }
}
