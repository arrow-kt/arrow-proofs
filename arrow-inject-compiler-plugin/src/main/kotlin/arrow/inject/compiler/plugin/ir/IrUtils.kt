package arrow.inject.compiler.plugin.ir

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.IrBuiltIns
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.IrBlockBodyBuilder
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrDeclarationParent
import org.jetbrains.kotlin.ir.declarations.IrFactory
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrReturnTarget
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrErrorExpression
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionAccessExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionExpressionImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrReturnImpl
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrValueParameterSymbol
import org.jetbrains.kotlin.ir.symbols.impl.IrValueParameterSymbolImpl
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.addAnnotations
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.statements
import org.jetbrains.kotlin.ir.visitors.IrElementTransformer
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

fun IrModuleFragment.irTransformFunctionBlockBodies(
  transformBody: (IrFunction) -> IrStatement?
): Unit =
  transformChildren(
    object : IrElementTransformer<Unit> {

      override fun visitFunction(declaration: IrFunction, data: Unit): IrStatement {
        return transformBody(declaration) ?: super.visitDeclaration(declaration, data)
      }
    },
    Unit
  )

fun IrStatement.transformFunctionAccess(
  transformFunctionAccess: (IrFunctionAccessExpression) -> IrStatement?
): Unit =
  transformChildren(
    object : IrElementTransformer<Unit> {

      override fun visitFunctionAccess(
        expression: IrFunctionAccessExpression,
        data: Unit
      ): IrElement =
        expression.transformChildren(this, Unit).let {
          transformFunctionAccess(expression) ?: super.visitFunctionAccess(expression, data)
        }
    },
    Unit
  )

fun IrBuiltIns.createIrReturn(parent: IrDeclarationParent, expression: IrExpression): IrReturn =
  IrReturnImpl(
    UNDEFINED_OFFSET,
    UNDEFINED_OFFSET,
    nothingType,
    (parent as IrReturnTarget).symbol,
    expression
  )

fun IrElement.transformNestedErrorExpressions(transform: (IrErrorExpression) -> IrExpression?) {
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

fun IrElement.transformIrValueParameter(transform: (IrValueParameter) -> IrStatement?) {
  transformChildren(
    object : IrElementTransformer<Unit> {

      override fun visitValueParameter(declaration: IrValueParameter, data: Unit): IrStatement =
        declaration.transformChildren(this, Unit).let {
          transform(declaration) ?: super.visitValueParameter(declaration, data)
        }
    },
    Unit
  )
}

fun IrFactory.createBlockBody(newStatements: List<IrStatement>): IrBlockBody =
  createBlockBody(UNDEFINED_OFFSET, UNDEFINED_OFFSET, newStatements)

fun IrFactory.createBlockBodyFromFunctionStatements(fn: IrSimpleFunction) =
  createBlockBody(fn.body?.statements.orEmpty())

fun IrPluginContext.topLevelFunctionSymbol(
  pck: String,
  callableName: String
): IrSimpleFunctionSymbol =
  referenceFunctions(
      CallableId(
        packageName = FqName(pck),
        className = null,
        callableName = Name.identifier(callableName)
      )
    )
    .first()

fun IrBuiltIns.createBodyReturningExpression(
  declarationParent: IrDeclarationParent,
  expression: IrExpression
): IrBlockBody {
  val newReturn = createIrReturn(declarationParent, expression)
  val newStatements = listOf(newReturn)
  return irFactory.createBlockBody(newStatements)
}

fun IrBuiltIns.returningBlockType(irDeclarationParent: IrDeclarationParent) =
  (irDeclarationParent as? IrFunction)?.returnType ?: nothingType

fun IrResolution.createLambdaExpressionWithoutParent(
  type: IrType,
  returningBlockType: IrType,
  paramSymbol: IrValueParameterSymbol,
  bodyGen: IrBlockBodyBuilder.() -> Unit
): IrFunctionExpression {
  val function =
    irFactory.buildFun {
      this.startOffset = UNDEFINED_OFFSET
      this.endOffset = UNDEFINED_OFFSET
      this.returnType = returningBlockType
      name = Name.identifier("<anonymous>")
      visibility = DescriptorVisibilities.LOCAL
      origin = IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA
    }
  function.body =
    DeclarationIrBuilder(this, function.symbol, UNDEFINED_OFFSET, UNDEFINED_OFFSET)
      .irBlockBody(UNDEFINED_OFFSET, UNDEFINED_OFFSET, bodyGen)

  function.extensionReceiverParameter = withFunReceiverParameter(function, type, paramSymbol)

  return IrFunctionExpressionImpl(
      UNDEFINED_OFFSET,
      UNDEFINED_OFFSET,
      irBuiltIns.nothingType,
      function,
      IrStatementOrigin.LAMBDA
    )
    .also {
      val extensionAnnotation =
        irBuiltIns.findClass(Name.identifier("ExtensionFunctionType"), "kotlin")
      it.type =
        irBuiltIns
          .functionN(1)
          .typeWith(listOf(type, returningBlockType))
          .addAnnotations(
            listOfNotNull(
              extensionAnnotation?.constructors?.firstOrNull()?.owner?.irCall()
                as? IrConstructorCall
            )
          )
    }
}

fun IrBuiltIns.addStatements(
  nestedLambdaFunction: IrSimpleFunction,
  statements: List<IrStatement>
) {
  val lambdaBlockBody = nestedLambdaFunction.body
  // last processing nests the remaining
  if (lambdaBlockBody is IrBlockBody) {
    statements.forEach {
      val patchedStatement =
        if (it is IrReturn) createIrReturn(nestedLambdaFunction, it.value) else it
      lambdaBlockBody.statements.add(patchedStatement)
    }
  }
}

fun IrResolution.createNestedLambda(
  declarationParent: IrDeclarationParent,
  type: IrType,
  returningBlockType: IrType,
  paramSymbol: IrValueParameterSymbolImpl
): IrFunctionExpression {
  return createLambdaExpressionWithoutParent(type, returningBlockType, paramSymbol) { blockBody {} }
    .also { it.function.parent = declarationParent }
}
