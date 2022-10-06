package arrow.inject.compiler.plugin.ir

import arrow.inject.compiler.plugin.fir.resolution.resolver.ProofCache
import arrow.inject.compiler.plugin.model.ProofAnnotationsFqName
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.IrGetValueImpl
import org.jetbrains.kotlin.ir.symbols.impl.IrValueParameterSymbolImpl
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.IrTypeSystemContextImpl
import org.jetbrains.kotlin.ir.util.dump
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.types.model.TypeSystemContext
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.MutableMap
import kotlin.collections.drop
import kotlin.collections.emptyList
import kotlin.collections.first
import kotlin.collections.forEachIndexed
import kotlin.collections.map
import kotlin.collections.mutableMapOf
import kotlin.collections.set

internal class ContextResolvedIrCodegen(
  override val proofCache: ProofCache,
  override val moduleFragment: IrModuleFragment,
  private val irPluginContext: IrPluginContext
) :
  IrPluginContext by irPluginContext,
  TypeSystemContext by IrTypeSystemContextImpl(irPluginContext.irBuiltIns),
  IrResolution,
  ProofsIrAbstractCodegen {

  fun generateContextResolvedBody() {
    moduleFragment.irTransformFunctionBlockBodies { parent ->
      if (parent.hasAnnotation(ProofAnnotationsFqName.ContextResolvedAnnotation)) {
        val mirrorFunction = parent.mirrorFunction()
        if (mirrorFunction != null) {
          val body = irFactory.createBlockBodyFromFunctionStatements(mirrorFunction)
          val steps = createCallAndContextReceiverType(mirrorFunction)
          val functionBody =
            processBodiesRecursive(parent, body, steps, null, emptyList(), steps.size)
          parent.body = functionBody
          println(parent.dump())
          parent
        } else null
      } else null
    }
  }

  private fun createCallAndContextReceiverType(mirrorFunction: IrFunction): List<Pair<IrCall, IrType>> =
    mirrorFunction.contextReceiversValueParameters.map { ctx ->
      val call = contextualFunction.owner.irCall() as IrCall
      val returningBlockType: IrType = irBuiltIns.returningBlockType(mirrorFunction)
      call.setTypeArguments(ctx.type, returningBlockType)
      Pair(call, ctx.type)
    }

  private tailrec fun processBodiesRecursive(
    declarationParent: IrDeclarationParent,
    body: IrBlockBody,
    steps: List<Pair<IrCall, IrType>>,
    previousLambda: IrSimpleFunction?,
    statements: List<IrStatement>,
    totalSteps: Int,
    internalSymbolState: MutableMap<IrType, IrValueParameterSymbolImpl> = mutableMapOf()
  ): IrBody =
    when {
      // done processing
      steps.isEmpty() -> body
      else -> {
        // current `with` call and context receiver type
        val (call, type) = steps.first()
        val paramSymbol = IrValueParameterSymbolImpl()
        if (previousLambda != null)
          insertCallInLambda(previousLambda, call)
        val nestedLambda = createNestedLambda(declarationParent, type, call.type, paramSymbol)
        val extensionReceiverParam = nestedLambda.function.extensionReceiverParameter
        requireNotNull(extensionReceiverParam) { "Expected extension receiver parameter" }
        setValueArgument0ToContextualReceiver(0, type, call, extensionReceiverParam)
        call.putValueArgument(1, nestedLambda)
        if (shouldNestStatementsOnNestedLambda(steps, totalSteps))
          irBuiltIns.addStatements(nestedLambda.function, statements)
        val transformedBody = irBuiltIns.createBodyReturningExpression(declarationParent, call)
        internalSymbolState[type] = paramSymbol
        replaceErrorExpressionsWithReceiverValues(transformedBody, internalSymbolState)
        processBodiesRecursive(
          declarationParent = nestedLambda.function,
          body = transformedBody,
          steps = steps.drop(1),
          previousLambda = nestedLambda.function,
          statements = statements,
          totalSteps = totalSteps,
          internalSymbolState = internalSymbolState
        )
      }
    }

  private fun shouldNestStatementsOnNestedLambda(
    steps: List<Pair<IrCall, IrType>>,
    originalStepsSize: Int
  ): Boolean = steps.size == 1 || originalStepsSize == 1

  private fun insertCallInLambda(
    previousStepLambdaFunction: IrSimpleFunction,
    replacementCall: IrCall
  ) {
    val returned = irBuiltIns.createIrReturn(previousStepLambdaFunction, replacementCall)
    val previousLambdaBody: IrBlockBody? = (previousStepLambdaFunction.body as? IrBlockBody)
    previousLambdaBody?.statements?.add(returned)
  }

  private fun replaceErrorExpressionsWithReceiverValues(
    transformedBody: IrBlockBody,
    paramSymbol: Map<IrType, IrValueParameterSymbolImpl>
  ) {
    transformedBody.transformNestedErrorExpressions { errorExpression ->
      val symbol = paramSymbol[errorExpression.type]
      if (symbol != null) {
        IrGetValueImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, symbol)
      } else errorExpression
    }
  }

  private fun setValueArgument0ToContextualReceiver(
    index: Int,
    irType: IrType,
    replacementCall: IrMemberAccessExpression<*>,
    receiverParam: IrValueParameter
  ) {
    contextProofCall(irType)?.apply {
      if (this is IrFunctionAccessExpression) {
        symbol.owner.contextReceiversValueParameters.forEachIndexed { index, param ->
          val targetType = targetType(irType, param.type)
          val resolvedType = targetType ?: param.type
          setValueArgument0ToContextualReceiver(index, resolvedType, this, param)
        }
      }
      if (replacementCall.valueArgumentsCount > index) {
        if (this is IrMemberAccessExpression<*> && this.valueArgumentsCount > index) {
          val valueArg = receiverParam.irCall()
          this.putValueArgument(index, valueArg)
        }
        replacementCall.putValueArgument(index, this)
      }
    }
  }

  private fun IrCall.setTypeArguments(targetType: IrType, returningBlockType: IrType) {
    putTypeArgument(0, targetType)
    putTypeArgument(1, returningBlockType)
    type = returningBlockType
  }

}
