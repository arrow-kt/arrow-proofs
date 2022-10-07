package arrow.inject.compiler.plugin.ir

import arrow.inject.compiler.plugin.fir.resolution.resolver.ProofCache
import arrow.inject.compiler.plugin.model.ProofAnnotationsFqName
import kotlin.collections.set
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.declarations.IrDeclarationParent
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrBody
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrFunctionAccessExpression
import org.jetbrains.kotlin.ir.expressions.IrMemberAccessExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrGetValueImpl
import org.jetbrains.kotlin.ir.symbols.IrValueParameterSymbol
import org.jetbrains.kotlin.ir.symbols.impl.IrValueParameterSymbolImpl
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.IrTypeSystemContextImpl
import org.jetbrains.kotlin.ir.util.DeepCopySymbolRemapper
import org.jetbrains.kotlin.ir.util.deepCopyWithSymbols
import org.jetbrains.kotlin.ir.util.dump
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.statements
import org.jetbrains.kotlin.types.model.TypeSystemContext

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
    moduleFragment.irTransformFunctionBlockBodies { function ->
      if (function.hasAnnotation(ProofAnnotationsFqName.ContextResolvedAnnotation)) {
        val mirrorFunction = function.mirrorFunction()
        if (mirrorFunction != null) {
          val body = irFactory.createBlockBodyFromFunctionStatements(mirrorFunction)
          val steps = createCallAndContextReceiverType(mirrorFunction)
          val clonedStatements =
            mirrorFunction.body?.statements.orEmpty().map { it.deepCopyWithSymbols() }
          val functionBody =
            processBodiesRecursive(
              declarationParent = function,
              body = body,
              steps = steps,
              previousLambda = null,
              statements = clonedStatements,
              totalSteps = steps.size,
            )
          function.body = functionBody
          println(function.dump())
          function
        } else null
      } else null
    }
  }

  private fun createCallAndContextReceiverType(
    mirrorFunction: IrFunction
  ): List<Pair<IrCall, IrType>> =
    mirrorFunction.contextReceiversValueParameters.flatMap { ctx ->
      getAllContextReceiversTypes(ctx.type, mutableListOf()).map { type ->
        val call = contextualFunction.owner.irCall() as IrCall
        val returningBlockType: IrType = irBuiltIns.returningBlockType(mirrorFunction)
        call.setTypeArguments(type, returningBlockType)
        Pair(call, type)
      }
    }

  private fun processBodiesRecursive(
    declarationParent: IrDeclarationParent,
    body: IrBlockBody,
    steps: List<Pair<IrCall, IrType>>,
    previousLambda: IrSimpleFunction?,
    statements: List<IrStatement>,
    totalSteps: Int
  ): IrBody =
    when {
      // done processing
      steps.isEmpty() -> body
      else -> {
        // current `with` call and context receiver type
        val (call, type) = steps.first()
        val paramSymbol = IrValueParameterSymbolImpl()
        if (previousLambda != null) insertCallInLambda(previousLambda, call)
        val nestedLambda = createNestedLambda(declarationParent, type, call.type, paramSymbol)
        val extensionReceiverParam = nestedLambda.function.extensionReceiverParameter
        requireNotNull(extensionReceiverParam) { "Expected extension receiver parameter" }
        val previousLambdaParameter = previousLambda?.extensionReceiverParameter
        val previousType = previousLambdaParameter?.type ?: type
        setValueArgument0ToContextualReceiver(
          index = 0,
          previousIrType = previousType,
          irType = type,
          replacementCall = call,
          receiverParam = previousLambdaParameter ?: extensionReceiverParam
        )
        call.putValueArgument(1, nestedLambda)
        if (shouldNestStatementsOnNestedLambda(steps, totalSteps)) {
          replaceMirrorReceiverExpressionWithReceiverValues(paramSymbol, statements)
          irBuiltIns.addStatements(nestedLambda.function, statements)
        }
        val transformedBody =
          if (steps.size == 1) body
          else irBuiltIns.createBodyReturningExpression(declarationParent, call)
        //internalSymbolState[type] = paramSymbol
        //replaceErrorExpressionsWithReceiverValues(transformedBody, internalSymbolState)
        processBodiesRecursive(
          declarationParent = nestedLambda.function,
          body = transformedBody,
          steps = steps.drop(1),
          previousLambda = nestedLambda.function,
          statements = statements,
          totalSteps = totalSteps
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

  private fun replaceMirrorReceiverExpressionWithReceiverValues(
    lambdaSymbol: IrValueParameterSymbol,
    statements: List<IrStatement>
  ) {
    statements.forEach {
      it.transformValueAccessExpression { valueAccess ->
        if (valueAccess.type == lambdaSymbol.owner.type) {
          IrGetValueImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, lambdaSymbol)
        } else null
      }
    }
  }

  private fun setValueArgument0ToContextualReceiver(
    index: Int,
    previousIrType: IrType,
    irType: IrType,
    replacementCall: IrMemberAccessExpression<*>,
    receiverParam: IrValueParameter
  ) {
    contextProofCall(irType)?.apply {
      if (this is IrFunctionAccessExpression) {
        symbol.owner.contextReceiversValueParameters.forEachIndexed { index, param ->
          val targetType = targetType(irType, param.type)
          val resolvedType = targetType ?: param.type
          setValueArgument0ToContextualReceiver(index, previousIrType, resolvedType, this, param)
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
