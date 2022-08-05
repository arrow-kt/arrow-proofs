package arrow.inject.compiler.plugin.services

import arrow.inject.compiler.plugin.fir.FirArrowInjectExtensionRegistrar
import arrow.inject.compiler.plugin.fir.resolution.resolver.ProofCache
import arrow.inject.compiler.plugin.ir.IrArrowInjectExtensionRegistrar
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter
import org.jetbrains.kotlin.test.model.TestModule
import org.jetbrains.kotlin.test.services.EnvironmentConfigurator
import org.jetbrains.kotlin.test.services.TestServices

class ExtensionRegistrarConfigurator(
  testServices: TestServices,
) : EnvironmentConfigurator(testServices) {
  override fun CompilerPluginRegistrar.ExtensionStorage.registerCompilerExtensions(
    module: TestModule,
    configuration: CompilerConfiguration
  ) {
    val proofCache = ProofCache()
    FirExtensionRegistrarAdapter.registerExtension(FirArrowInjectExtensionRegistrar(proofCache))
    IrGenerationExtension.registerExtension(IrArrowInjectExtensionRegistrar(proofCache))
  }
}
