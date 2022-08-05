package arrow.inject.compiler.plugin

import arrow.inject.compiler.plugin.fir.FirArrowInjectExtensionRegistrar
import arrow.inject.compiler.plugin.fir.resolution.resolver.ProofCache
import arrow.inject.compiler.plugin.ir.IrArrowInjectExtensionRegistrar
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter

class ArrowInjectComponentRegistrar : CompilerPluginRegistrar() {

  override val supportsK2: Boolean = true

  override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
    val proofCache = ProofCache()
    FirExtensionRegistrarAdapter.registerExtension(FirArrowInjectExtensionRegistrar(proofCache))
    IrGenerationExtension.registerExtension(IrArrowInjectExtensionRegistrar(proofCache))
  }
}
