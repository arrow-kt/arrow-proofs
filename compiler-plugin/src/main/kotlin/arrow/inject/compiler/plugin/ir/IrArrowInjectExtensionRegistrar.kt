package arrow.inject.compiler.plugin.ir

import arrow.inject.compiler.plugin.model.ProofAnnotationsFqName
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationContainer
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.visitors.IrElementTransformer

class IrArrowInjectExtensionRegistrar : IrGenerationExtension {

  override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
    moduleFragment.irCall { irCall ->
      ProofsIrCodegen(moduleFragment, pluginContext).proveCall(irCall)
    }

    moduleFragment.removeCompileTimeDeclarations()
  }

  private fun IrModuleFragment.irCall(function: (IrCall) -> IrElement?): Unit =
    transformChildren(
      object : IrElementTransformer<Unit> {
        override fun visitCall(expression: IrCall, data: Unit): IrElement =
          expression.transformChildren(this, Unit).let {
            function(expression) ?: super.visitCall(expression, data)
          }
      },
      Unit
    )

  private fun IrModuleFragment.removeCompileTimeDeclarations() {
    files.forEach { file ->
      file.removeDeclaration {
        it.annotations.hasAnnotation(ProofAnnotationsFqName.CompileTimeAnnotation)
      }
    }
  }

  private fun IrDeclarationContainer.removeDeclaration(removeIf: (IrDeclaration) -> Boolean) {
    declarations.removeIf(removeIf)
    declarations.forEach {
      if (it is IrDeclarationContainer) it.removeDeclaration(removeIf)
    }
  }
}
