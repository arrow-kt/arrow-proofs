package arrow.inject.compiler.plugin.services

import arrow.inject.compiler.plugin.fir.FirArrowInjectExtensionRegistrar
import arrow.inject.compiler.plugin.fir.resolution.ProofCache
import arrow.inject.compiler.plugin.ir.IrArrowInjectExtensionRegistrar
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
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
    val proofCache: ProofCache = ProofCache()
    FirExtensionRegistrar.registerExtension(project, FirArrowInjectExtensionRegistrar(proofCache))
    IrGenerationExtension.registerExtension(project, IrArrowInjectExtensionRegistrar(proofCache))
  }
}
