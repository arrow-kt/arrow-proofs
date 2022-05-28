@file:OptIn(ObsoleteDescriptorBasedAPI::class)

package arrow.inject.compiler.plugin.ir

import arrow.inject.compiler.plugin.fir.resolution.resolver.ProofCache
import arrow.inject.compiler.plugin.model.Proof
import arrow.inject.compiler.plugin.model.ProofAnnotationsFqName
import arrow.inject.compiler.plugin.model.asProofCacheKey
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.TypeParameterDescriptor
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.fir.declarations.FirClass
import org.jetbrains.kotlin.fir.declarations.FirConstructor
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.FirMemberDeclaration
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.utils.visibility
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.ObsoleteDescriptorBasedAPI
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.IrBlockBodyBuilder
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrDeclarationParent
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrReturnTarget
import org.jetbrains.kotlin.ir.declarations.IrTypeParametersContainer
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.descriptors.toIrBasedKotlinType
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrBody
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionAccessExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression
import org.jetbrains.kotlin.ir.expressions.IrMemberAccessExpression
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstructorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionExpressionImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetObjectValueImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetValueImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrReturnImpl
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrValueParameterSymbol
import org.jetbrains.kotlin.ir.symbols.impl.IrValueParameterSymbolImpl
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.IrTypeSystemContextImpl
import org.jetbrains.kotlin.ir.types.addAnnotations
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.IdSignature
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.dumpKotlinLike
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.util.getPackageFragment
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.ir.util.render
import org.jetbrains.kotlin.ir.util.statements
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.model.KotlinTypeMarker
import org.jetbrains.kotlin.types.model.TypeArgumentMarker
import org.jetbrains.kotlin.types.model.TypeSystemContext
import org.jetbrains.kotlin.types.typeUtil.asTypeProjection

internal class ProofsIrContextReceiversCodegen(
  override val proofCache: ProofCache,
  private val moduleFragment: IrModuleFragment,
  private val irPluginContext: IrPluginContext
) :
  IrPluginContext by irPluginContext,
  TypeSystemContext by IrTypeSystemContextImpl(irPluginContext.irBuiltIns), ProofsIrCodegenAbstractComponent {

  fun proveBody(declarationParent: IrDeclarationParent, body: IrBlockBody): IrBody =
    insertContextScope(declarationParent, body)

  private fun isContextOfCall(call: IrFunctionAccessExpression) =
    call.symbol.owner.fqNameWhenAvailable == FqName("arrow.inject.annotations.ResolveKt.contextOf")

  private fun insertContextScope(declarationParent: IrDeclarationParent, body: IrBlockBody): IrBody =
    replacementContextCall(declarationParent, body)

  private fun IrElement.contextCall(): IrFunctionAccessExpression? =
    if (this is IrCall && isContextOfCall(this)) this
    else {
      var expression: IrFunctionAccessExpression? = null
      acceptChildrenVoid(object : IrElementVisitorVoid {
        override fun visitElement(element: IrElement) {
          if (element is IrCall && isContextOfCall(element)) {
            expression = element
          }
        }
      })
      expression
    }


  private fun replacementContextCall(
    declarationParent: IrDeclarationParent,
    body: IrBlockBody
  ): IrBody {
    val withFunction =
      irBuiltIns.findFunctions(Name.identifier("contextual"), FqName("arrow.inject.annotations")).first()
    val replacementCall = withFunction.owner.irCall() as IrCallImpl
    val returningBlockType = (declarationParent as? IrFunction)?.returnType ?: irBuiltIns.nothingType
    val contextCall = body.contextCall()
    val targetType = contextCall?.getTypeArgument(0)
    replacementCall.also {
      if (targetType != null)
        it.putTypeArgument(0, targetType)
      it.putTypeArgument(1, returningBlockType)
      it.type = returningBlockType
    }
    val transformedBody: IrBody? =
      if (contextCall != null && targetType != null) {
        val targetKotlinType = targetType.toIrBasedKotlinType()
        val proofCall = givenProofCall(ProofAnnotationsFqName.ProviderAnnotation, targetKotlinType)
        replacementCall.putValueArgument(0, proofCall)
        val remaining = body.remainingStatementsAfterCall(contextCall)
        val paramSymbol = IrValueParameterSymbolImpl()
        val nestedBody: IrFunctionExpression =
          createLambdaExpression(declarationParent, targetType, returningBlockType, withFunction, paramSymbol) {
            val body = blockBody {
            }

            remaining.forEach {
              body.statements.add(it)
            }
          }
        replacementCall.putValueArgument(1, nestedBody)
        val statementsBeforeContext = body.statements.takeWhile { it.contextCall() == null }
        val newStatements = statementsBeforeContext +
          IrReturnImpl(
            UNDEFINED_OFFSET,
            UNDEFINED_OFFSET,
            irBuiltIns.nothingType,
            (declarationParent as IrReturnTarget).symbol,
            replacementCall
          )
        val transformedBody = irFactory.createBlockBody(UNDEFINED_OFFSET, UNDEFINED_OFFSET, newStatements)
        transformedBody.transformNestedErrorExpressions { errorExpression ->
          if (errorExpression.type == targetType) {
            IrGetValueImpl(
              UNDEFINED_OFFSET,
              UNDEFINED_OFFSET,
              paramSymbol
            )
          } else errorExpression
        }
        transformedBody
      } else null
    //return replacementCall
    return transformedBody ?: body
  }

  private fun <D> withFunReceiverParameter(
    dec: D,
    targetType: IrType,
    paramSymbol: IrValueParameterSymbol
  ): IrValueParameter where D : IrDeclaration, D : IrDeclarationParent =
    irFactory.createValueParameter(
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
    ).also {
      it.parent = dec
    }
//    buildValueParameter(
//      dec
//    ) {
//      this.type = targetType
//      this.name = Name.identifier("${'$'}this${'$'}contextual")
//    }.also {
//      it.parent = dec
//    }
//    valueParameters.first().run {
//      val param = (dec as? IrFunction)?.extensionReceiverParameter!!
//      return param.also {
//        it.type = targetType
//        it.name = param.name
//      }
//    }

  fun createLambdaExpression(
    parent: IrDeclarationParent,
    type: IrType,
    returningBlockType: IrType,
    withFunction: IrSimpleFunctionSymbol,
    paramSymbol: IrValueParameterSymbol,
    bodyGen: IrBlockBodyBuilder.() -> Unit
  ): IrFunctionExpression {
    val function = irFactory.buildFun {
      this.startOffset = UNDEFINED_OFFSET
      this.endOffset = UNDEFINED_OFFSET
      this.returnType = returningBlockType
      name = Name.identifier("<anonymous>")
      visibility = DescriptorVisibilities.LOCAL
      origin = IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA
    }
    function.body =
      DeclarationIrBuilder(irPluginContext, function.symbol, UNDEFINED_OFFSET, UNDEFINED_OFFSET).irBlockBody(
        UNDEFINED_OFFSET,
        UNDEFINED_OFFSET,
        bodyGen
      )
    function.parent = parent
    //$receiver: VALUE_PARAMETER name:$this$with type:foo.bar.Persistence
    function.extensionReceiverParameter = withFunReceiverParameter(function, type, paramSymbol)

    return IrFunctionExpressionImpl(
      UNDEFINED_OFFSET,
      UNDEFINED_OFFSET,
      irBuiltIns.nothingType,
      function,
      IrStatementOrigin.LAMBDA
    ).also {
      val extensionAnnotation = irBuiltIns.findClass(Name.identifier("ExtensionFunctionType"), "kotlin")
      it.type = irBuiltIns.functionN(1).typeWith(listOf(type, returningBlockType)).addAnnotations(
        listOfNotNull(
          extensionAnnotation
            ?.constructors?.firstOrNull()?.owner?.irCall()
            as? IrConstructorCall
        )
      )
    }
  }

  private fun IrBody.remainingStatementsAfterCall(call: IrFunctionAccessExpression): List<IrStatement> {
    val remaining = statements.dropWhile {
      !it.containsNestedElement(call)
    }
    return remaining.drop(1)
  }

  private fun replacementCall(irCall: IrFunctionAccessExpression): IrMemberAccessExpression<*> {
    val packageFqName = checkNotNull(irCall.symbol.owner.getPackageFragment()).fqName.asString()
    val functionFqName = checkNotNull(irCall.symbol.owner.kotlinFqName).asString()

    val signature = IdSignature.CommonSignature(packageFqName, functionFqName, null, 0)

    symbols.externalSymbolTable.referenceSimpleFunction(signature)

    val mirrorFunction: IrFunction? =
      moduleFragment.files
        .flatMap { it.declarations }
        .firstNotNullOfOrNull { it.mirrorFunction(functionFqName) }

    checkNotNull(mirrorFunction) {
      "Expected mirror function for fake call ${irCall.render()} is null"
    }

    val replacementCall: IrExpression = mirrorFunction.symbol.owner.irCall()

    if (replacementCall is IrFunctionAccessExpression) {
      irCall.typeArguments.forEach { (index, irType) ->
        if (replacementCall.typeArgumentsCount > index && irType != null) {
          replacementCall.putTypeArgument(index, irType)
        }
      }
      irCall.valueArguments.forEach { (index, irType) ->
        if (replacementCall.valueArgumentsCount > index && irType != null) {
          replacementCall.putValueArgument(index, irType)
        }
      }

      replacementCall.dispatchReceiver = irCall.dispatchReceiver
      replacementCall.extensionReceiver = irCall.extensionReceiver
    } else {
      error("Unsupported replacement call: ${replacementCall.render()}")
    }

    return replacementCall
  }

  private fun Proof.irDeclaration(): IrDeclaration =
    when (declaration) {
      is FirClass -> symbolTable.referenceClass(idSignature).constructors.first().owner
      is FirConstructor -> symbolTable.referenceConstructor(idSignature).owner
      is FirFunction -> symbolTable.referenceSimpleFunction(idSignature).owner
      is FirProperty -> symbolTable.referenceProperty(idSignature).owner
      else -> error("Unsupported FirDeclaration: $this")
    }

  private fun IrDeclaration.type(): IrType =
    when (this) {
      is IrClass -> defaultType
      is IrFunction -> returnType
      is IrProperty -> checkNotNull(getter?.returnType) { "Expected getter" }
      else -> error("Unsupported IrDeclaration: $this")
    }
}
