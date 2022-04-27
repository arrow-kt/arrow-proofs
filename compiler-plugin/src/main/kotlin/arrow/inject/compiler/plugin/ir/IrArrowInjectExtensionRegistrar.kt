package arrow.inject.compiler.plugin.ir

import arrow.inject.compiler.plugin.fir.utils.FirUtils
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.util.dump
import org.jetbrains.kotlin.ir.util.dumpKotlinLike
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.visitors.IrElementTransformer

class IrArrowInjectExtensionRegistrar : IrGenerationExtension {

  override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
    moduleFragment.irCall { irCall ->
      ProofsIrCodegen(moduleFragment, pluginContext).proveNestedCalls(irCall)
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
}

private fun IrModuleFragment.removeCompileTimeDeclarations() {
  files.forEach { file ->
    file.declarations.removeIf { it.annotations.hasAnnotation(FirUtils.CompileTimeAnnotation) }
  }
}
