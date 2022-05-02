package arrow.inject.compiler.plugin.fir

import arrow.inject.compiler.plugin.fir.codegen.ResolvedFunctionGenerationExtension
import arrow.inject.compiler.plugin.fir.resolution.ProofCache
import arrow.inject.compiler.plugin.fir.resolution.ProofResolutionCallCheckerExtension
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

class FirArrowInjectExtensionRegistrar(
  private val proofCache: ProofCache,
) : FirExtensionRegistrar() {

  override fun ExtensionRegistrarContext.configurePlugin() {
    +::ResolvedFunctionGenerationExtension
    +{ session: FirSession -> ProofResolutionCallCheckerExtension(proofCache, session) }
  }
}
