package arrow.inject.compiler.plugin.services

import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.test.model.TestModule
import org.jetbrains.kotlin.test.services.EnvironmentConfigurator
import org.jetbrains.kotlin.test.services.TestServices

// Unbound symbols issue:
// https://youtrack.jetbrains.com/issue/KT-53505/IllegalStateException-IR-Symbols-are-unbound-in-Kotlin-180-dev-1006
class LinkViaSignaturesConfigurator(
  testServices: TestServices,
) : EnvironmentConfigurator(testServices) {
  override fun CompilerPluginRegistrar.ExtensionStorage.registerCompilerExtensions(
    module: TestModule,
    configuration: CompilerConfiguration
  ) {
    configuration.put(JVMConfigurationKeys.LINK_VIA_SIGNATURES, true)
  }
}
