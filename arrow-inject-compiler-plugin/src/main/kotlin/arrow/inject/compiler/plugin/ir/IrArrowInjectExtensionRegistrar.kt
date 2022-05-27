package arrow.inject.compiler.plugin.ir

import arrow.inject.compiler.plugin.fir.resolution.resolver.ProofCache
import arrow.inject.compiler.plugin.model.ProofAnnotationsFqName
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.serialization.proto.IrErrorCallExpression
import org.jetbrains.kotlin.fir.FirTargetElement
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationBase
import org.jetbrains.kotlin.ir.declarations.IrDeclarationContainer
import org.jetbrains.kotlin.ir.declarations.IrDeclarationParent
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrBody
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrErrorExpression
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionAccessExpression
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.visitors.IrElementTransformer
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid

public class IrArrowInjectExtensionRegistrar(
  private val proofCache: ProofCache,
) : IrGenerationExtension {

  override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
    ProofsIrCodegen(proofCache, moduleFragment, pluginContext).run {
      moduleFragment.irFunctionAccessExpression { call ->
        proveCall(call)
      }

      // context receivers
      moduleFragment.irTransformBlockBodies { parent, body ->
        proveBody(parent, body)
      }

      moduleFragment.removeCompileTimeDeclarations()
    }
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

fun IrBlockBody.findReturnExpression(): IrExpression? {
  var returnExpression: IrExpression? = null

  val visitor = object : IrElementVisitorVoid {
    override fun visitElement(element: IrElement) {
      if (element is IrReturn) {
        returnExpression =element
      } else element.acceptChildrenVoid(this)
    }
  }
  acceptChildrenVoid(visitor)
  return returnExpression
}

fun IrElement.containsNestedElement(
  targetElement: IrElement
): Boolean {
  var containsCall = this == targetElement
  if (!containsCall) {
    val visitor = object : IrElementVisitorVoid {
      override fun visitElement(element: IrElement) {
        if (!containsCall) {
          containsCall = element == targetElement
          element.acceptChildrenVoid(this)
        }
      }

    }
    acceptChildrenVoid(visitor)
  }
  return containsCall
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

private fun IrModuleFragment.irTransformBlockBodies(
  transformBody: (IrDeclarationParent, IrBlockBody) -> IrBody?
): Unit =
  transformChildren(
    object : IrElementTransformer<Unit> {

      var declarationParent: IrDeclarationParent? = null

      override fun visitDeclaration(declaration: IrDeclarationBase, data: Unit): IrStatement {
        if (declaration is IrDeclarationParent) {
          declarationParent = declaration
        }
        return super.visitDeclaration(declaration, data)
      }

      override fun visitBlockBody(body: IrBlockBody, data: Unit): IrBody =
        body.transformChildren(this, Unit).let {
          val parent = declarationParent
          val result =
            if (parent != null)
              transformBody(parent, body)
            else null
          result ?: super.visitBlockBody(body, data)
        }
    },
    Unit
  )

fun IrElement.transformNestedErrorExpressions(
  transform: (IrErrorExpression) -> IrExpression?
): Unit {
  transformChildren(
    object : IrElementTransformer<Unit> {

      override fun visitErrorExpression(expression: IrErrorExpression, data: Unit): IrExpression =
        expression.transformChildren(this, Unit).let {
          transform(expression) ?: super.visitErrorExpression(expression, data)
        }

    },
    Unit
  )
}
