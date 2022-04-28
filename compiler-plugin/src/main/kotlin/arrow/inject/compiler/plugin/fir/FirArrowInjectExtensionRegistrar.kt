package arrow.inject.compiler.plugin.fir

import arrow.inject.compiler.plugin.fir.codegen.ResolvedFunctionGenerationExtension
import arrow.inject.compiler.plugin.fir.resolution.ProofResolutionCallCheckerExtension
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

class FirArrowInjectExtensionRegistrar : FirExtensionRegistrar() {

  override fun ExtensionRegistrarContext.configurePlugin() {
    +::ResolvedFunctionGenerationExtension
    +::ProofResolutionCallCheckerExtension
  }
}
