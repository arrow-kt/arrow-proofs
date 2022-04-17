package arrow.inject.compiler.plugin.services

import com.intellij.openapi.project.Project
import arrow.inject.compiler.plugin.ArrowInjectExtensionRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar
import org.jetbrains.kotlin.test.model.TestModule
import org.jetbrains.kotlin.test.services.EnvironmentConfigurator
import org.jetbrains.kotlin.test.services.TestServices

class ExtensionRegistrarConfigurator(
  testServices: TestServices,
) : EnvironmentConfigurator(testServices) {
  override fun registerCompilerExtensions(
    project: Project,
    module: TestModule,
    configuration: CompilerConfiguration
  ) {
    FirExtensionRegistrar.registerExtension(project, ArrowInjectExtensionRegistrar())
    //        IrGenerationExtension.registerExtension(project, SimpleIrGenerationExtension())
  }
}
