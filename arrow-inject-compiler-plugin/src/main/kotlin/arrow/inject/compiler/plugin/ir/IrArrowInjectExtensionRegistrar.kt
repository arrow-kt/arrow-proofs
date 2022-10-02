package arrow.inject.compiler.plugin.ir

import arrow.inject.compiler.plugin.fir.resolution.resolver.ProofCache
import arrow.inject.compiler.plugin.model.ProofAnnotationsFqName
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationContainer
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.util.hasAnnotation

public class IrArrowInjectExtensionRegistrar(
  private val proofCache: ProofCache,
) : IrGenerationExtension {

  override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
    ProofsIrContextReceiversCodegen(proofCache, moduleFragment, pluginContext)
      .generateContextReceivers()

    ContextResolvedIrCodegen(proofCache, moduleFragment, pluginContext)
      .generateContextResolvedBody()

    moduleFragment.removeCompileTimeDeclarations()
  }

  private fun IrModuleFragment.removeCompileTimeDeclarations() {
    files.forEach { file ->
      file.removeDeclaration {
        it.annotations.hasAnnotation(ProofAnnotationsFqName.CompileTimeAnnotation)
      }
    }
  }

  private fun IrDeclarationContainer.removeDeclaration(removeIf: (IrDeclaration) -> Boolean) {
    declarations.removeIf(removeIf)
    declarations.forEach { if (it is IrDeclarationContainer) it.removeDeclaration(removeIf) }
  }
}
