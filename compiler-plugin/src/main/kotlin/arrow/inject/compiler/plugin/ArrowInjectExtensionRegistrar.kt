package arrow.inject.compiler.plugin

import arrow.inject.compiler.plugin.fir.ContextInstanceGenerationExtension
import arrow.inject.compiler.plugin.fir.ProofResolutionCallCheckerExtension
import com.intellij.mock.MockProject
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analyzer.AnalysisResult
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.container.ComponentProvider
import org.jetbrains.kotlin.context.ProjectContext
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.BindingTrace
import org.jetbrains.kotlin.resolve.extensions.AnalysisHandlerExtension

class ArrowInjectExtensionRegistrar : FirExtensionRegistrar() {

  override fun ExtensionRegistrarContext.configurePlugin() {
    +::ContextInstanceGenerationExtension
    +::ProofResolutionCallCheckerExtension
  }
}
