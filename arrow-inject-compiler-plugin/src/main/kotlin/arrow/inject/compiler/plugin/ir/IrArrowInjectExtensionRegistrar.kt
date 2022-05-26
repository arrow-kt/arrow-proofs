package arrow.inject.compiler.plugin.ir

import arrow.inject.compiler.plugin.fir.resolution.resolver.ProofCache
import arrow.inject.compiler.plugin.model.ProofAnnotationsFqName
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationContainer
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.expressions.IrFunctionAccessExpression
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.visitors.IrElementTransformer

public class IrArrowInjectExtensionRegistrar(
  private val proofCache: ProofCache,
) : IrGenerationExtension {

  override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
    moduleFragment.irFunctionAccessExpression { call ->
      ProofsIrCodegen(proofCache, moduleFragment, pluginContext).proveCall(call)
    }

    moduleFragment.removeCompileTimeDeclarations()
  }

  private fun IrModuleFragment.irFunctionAccessExpression(
    call: (IrFunctionAccessExpression) -> IrElement?
  ): Unit =
    transformChildren(
      object : IrElementTransformer<Unit> {
        override fun visitFunctionAccess(
          expression: IrFunctionAccessExpression,
          data: Unit
        ): IrElement =
          expression.transformChildren(this, Unit).let {
            call(expression) ?: super.visitFunctionAccess(expression, data)
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
    declarations.forEach { if (it is IrDeclarationContainer) it.removeDeclaration(removeIf) }
  }
}
