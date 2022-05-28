package arrow.inject.compiler.plugin.ir

import arrow.inject.compiler.plugin.fir.resolution.resolver.ProofCache
import arrow.inject.compiler.plugin.model.ProofAnnotationsFqName.ProviderAnnotation
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.IrBlockBodyBuilder
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationBase
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrDeclarationParent
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrReturnTarget
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.descriptors.toIrBasedKotlinType
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrBody
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrErrorExpression
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionAccessExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionExpressionImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetValueImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrReturnImpl
import org.jetbrains.kotlin.ir.symbols.IrValueParameterSymbol
import org.jetbrains.kotlin.ir.symbols.impl.IrValueParameterSymbolImpl
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.IrTypeSystemContextImpl
import org.jetbrains.kotlin.ir.types.addAnnotations
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.util.statements
import org.jetbrains.kotlin.ir.visitors.IrElementTransformer
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.model.TypeSystemContext

internal class ProofsIrContextReceiversCodegen(
  override val proofCache: ProofCache,
  override val moduleFragment: IrModuleFragment,
  private val irPluginContext: IrPluginContext
) :
  IrPluginContext by irPluginContext,
  TypeSystemContext by IrTypeSystemContextImpl(irPluginContext.irBuiltIns),
  ProofsIrAbstractCodegen {

  fun generateContextReceivers() {
    irTransformBlockBodies { parent, body -> replacementContextCall(parent, body) }
  }

  private fun replacementContextCall(
    declarationParent: IrDeclarationParent,
    body: IrBlockBody
  ): IrBody {
    val replacementCall = replacementCall()
    val returningBlockType = declarationParent.returningBlockType()
    val contextCall = body.contextCall()
    val targetType = contextCall?.getTypeArgument(0)

    replacementCall.addReplacedTypeArguments(targetType, returningBlockType)

    return replacementCall.transformedBody(
      contextCall,
      targetType,
      body,
      declarationParent,
      returningBlockType
    )
      ?: body
  }

  private fun IrDeclarationParent.returningBlockType() =
    (this as? IrFunction)?.returnType ?: irBuiltIns.nothingType

  private fun IrCall.transformedBody(
    contextCall: IrFunctionAccessExpression?,
    targetType: IrType?,
    body: IrBlockBody,
    declarationParent: IrDeclarationParent,
    returningBlockType: IrType
  ) =
    if (contextCall != null && targetType != null) {
      val targetKotlinType = targetType.toIrBasedKotlinType()
      val paramSymbol = IrValueParameterSymbolImpl()
      putSubjectValueArgument(targetKotlinType)
      putReceiverLambdaValueArgument(
        declarationParent,
        targetType,
        contextCall,
        returningBlockType,
        paramSymbol,
        body
      )
      val statementsBeforeContext = body.statements.takeWhile { it.contextCall() == null }
      val newStatements = statementsBeforeContext + declarationParent.createIrReturn(this)
      val transformedBody = createBlockBody(newStatements)
      replaceErrorExpressionsWithReceiverValues(transformedBody, targetType, paramSymbol)
      transformedBody
    } else null

  private fun createBlockBody(newStatements: List<IrStatement>): IrBlockBody =
    irFactory.createBlockBody(UNDEFINED_OFFSET, UNDEFINED_OFFSET, newStatements)

  private fun replaceErrorExpressionsWithReceiverValues(
    transformedBody: IrBlockBody,
    targetType: IrType?,
    paramSymbol: IrValueParameterSymbolImpl
  ) {
    transformedBody.transformNestedErrorExpressions { errorExpression ->
      if (errorExpression.type == targetType) {
        IrGetValueImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, paramSymbol)
      } else errorExpression
    }
  }

  private fun IrDeclarationParent.createIrReturn(expression: IrExpression): IrReturn =
    IrReturnImpl(
      UNDEFINED_OFFSET,
      UNDEFINED_OFFSET,
      irBuiltIns.nothingType,
      (this as IrReturnTarget).symbol,
      expression
    )

  private fun IrCall.putReceiverLambdaValueArgument(
    declarationParent: IrDeclarationParent,
    targetType: IrType,
    contextCall: IrFunctionAccessExpression,
    returningBlockType: IrType,
    paramSymbol: IrValueParameterSymbolImpl,
    body: IrBlockBody
  ) {
    putValueArgument(
      1,
      createNestedLambdaBody(
        declarationParent,
        targetType,
        contextCall,
        returningBlockType,
        paramSymbol,
        body
      )
    )
  }

  private fun IrCall.putSubjectValueArgument(targetKotlinType: KotlinType) {
    putValueArgument(0, givenProofCall(ProviderAnnotation, targetKotlinType))
  }

  private fun createNestedLambdaBody(
    declarationParent: IrDeclarationParent,
    targetType: IrType,
    contextCall: IrFunctionAccessExpression,
    returningBlockType: IrType,
    paramSymbol: IrValueParameterSymbolImpl,
    blockBody: IrBlockBody
  ) =
    createLambdaExpression(declarationParent, targetType, returningBlockType, paramSymbol) {
      val newBlockBody = blockBody {}
      val remaining = blockBody.remainingStatementsAfterCall(contextCall)
      remaining.forEach { newBlockBody.statements.add(it) }
    }

  private fun IrCall.addReplacedTypeArguments(targetType: IrType?, returningBlockType: IrType) {
    if (targetType != null) putTypeArgument(0, targetType)
    putTypeArgument(1, returningBlockType)
    type = returningBlockType
  }

  private val contextualFunction =
    irBuiltIns
      .findFunctions(Name.identifier("contextual"), FqName("arrow.inject.annotations"))
      .first()

  private fun replacementCall(): IrCall = contextualFunction.owner.irCall() as IrCall

  private fun <D> withFunReceiverParameter(
    dec: D,
    targetType: IrType,
    paramSymbol: IrValueParameterSymbol
  ): IrValueParameter where D : IrDeclaration, D : IrDeclarationParent =
    irFactory
      .createValueParameter(
        startOffset = UNDEFINED_OFFSET,
        endOffset = UNDEFINED_OFFSET,
        origin = IrDeclarationOrigin.DEFINED,
        symbol = paramSymbol,
        name = Name.identifier("${'$'}this${'$'}contextual"),
        index = -1,
        type = targetType,
        varargElementType = null,
        isAssignable = false,
        isCrossinline = false,
        isHidden = false,
        isNoinline = false
      )
      .also { it.parent = dec }

  private fun createLambdaExpression(
    parent: IrDeclarationParent,
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
      DeclarationIrBuilder(irPluginContext, function.symbol, UNDEFINED_OFFSET, UNDEFINED_OFFSET)
        .irBlockBody(UNDEFINED_OFFSET, UNDEFINED_OFFSET, bodyGen)
    function.parent = parent

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

  private fun IrBody.remainingStatementsAfterCall(
    call: IrFunctionAccessExpression
  ): List<IrStatement> {
    val remaining = statements.dropWhile { !it.containsNestedElement(call) }
    return remaining.drop(1)
  }

  private fun IrElement.containsNestedElement(targetElement: IrElement): Boolean {
    var containsCall = this == targetElement
    if (!containsCall) {
      val visitor =
        object : IrElementVisitorVoid {
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

  private fun IrElement.transformNestedErrorExpressions(
    transform: (IrErrorExpression) -> IrExpression?
  ) {
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

  private val IrFunctionAccessExpression.isContextOfCall
    get() =
      symbol.owner.fqNameWhenAvailable == FqName("arrow.inject.annotations.ResolveKt.contextOf")

  private fun IrElement.contextCall(): IrFunctionAccessExpression? =
    if (this is IrCall && isContextOfCall) this
    else {
      var expression: IrFunctionAccessExpression? = null
      acceptChildrenVoid(
        object : IrElementVisitorVoid {
          override fun visitElement(element: IrElement) {
            if (element is IrCall && element.isContextOfCall) {
              expression = element
            }
          }
        }
      )
      expression
    }

  private fun irTransformBlockBodies(
    transformBody: (IrDeclarationParent, IrBlockBody) -> IrBody?
  ): Unit =
    moduleFragment.transformChildren(
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
            val result = if (parent != null) transformBody(parent, body) else null
            result ?: super.visitBlockBody(body, data)
          }
      },
      Unit
    )
}
