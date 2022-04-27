package arrow.inject.compiler.plugin.fir

import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

class FirArrowInjectExtensionRegistrar : FirExtensionRegistrar() {

  override fun ExtensionRegistrarContext.configurePlugin() {
    +::ContextInstanceGenerationExtension
    +::ProofResolutionCallCheckerExtension
  }
}
