package arrow.inject.compiler.plugin

import arrow.inject.compiler.plugin.fir.FirArrowInjectExtensionRegistrar
import arrow.inject.compiler.plugin.fir.resolution.resolver.ProofCache
import arrow.inject.compiler.plugin.ir.IrArrowInjectExtensionRegistrar
//import com.intellij.mock.MockProject
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.com.intellij.mock.MockProject
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

class ArrowInjectComponentRegistrar : ComponentRegistrar {

  override fun registerProjectComponents(
    project: MockProject,
    configuration: CompilerConfiguration
  ) {
    val proofCache = ProofCache()
    FirExtensionRegistrar.registerExtension(project, FirArrowInjectExtensionRegistrar(proofCache))
    IrGenerationExtension.registerExtension(project, IrArrowInjectExtensionRegistrar(proofCache))
  }
}
