package arrow.inject.compiler.plugin.ir

import arrow.inject.compiler.plugin.fir.resolution.resolver.ProofCache
import arrow.inject.compiler.plugin.model.Proof
import arrow.inject.compiler.plugin.model.ProofAnnotationsFqName
import arrow.inject.compiler.plugin.model.ProofResolution
import arrow.inject.compiler.plugin.model.asProofCacheKey
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.fir.declarations.FirMemberDeclaration
import org.jetbrains.kotlin.fir.declarations.utils.visibility
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.IrBlockBodyBuilder
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrDeclarationParent
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrReturnTarget
import org.jetbrains.kotlin.ir.declarations.IrTypeParametersContainer
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
import org.jetbrains.kotlin.ir.expressions.IrMemberAccessExpression
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionExpressionImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetValueImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrReturnImpl
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrValueParameterSymbol
import org.jetbrains.kotlin.ir.symbols.impl.IrValueParameterSymbolImpl
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.IrTypeArgument
import org.jetbrains.kotlin.ir.types.IrTypeSubstitutor
import org.jetbrains.kotlin.ir.types.IrTypeSystemContextImpl
import org.jetbrains.kotlin.ir.types.addAnnotations
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.dump
import org.jetbrains.kotlin.ir.util.dumpKotlinLike
import org.jetbrains.kotlin.ir.util.getPackageFragment
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.statements
import org.jetbrains.kotlin.ir.visitors.IrElementTransformer
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.types.model.TypeSystemContext

internal class ContextResolvedIrCodegen(
  override val proofCache: ProofCache,
  override val moduleFragment: IrModuleFragment,
  private val irPluginContext: IrPluginContext
) :
  IrPluginContext by irPluginContext,
  TypeSystemContext by IrTypeSystemContextImpl(irPluginContext.irBuiltIns),
  ProofsIrAbstractCodegen {

  private val contextualFunction: IrSimpleFunctionSymbol
    get() =
      referenceFunctions(
          CallableId(
            packageName = FqName("arrow.inject.annotations"),
            className = null,
            callableName = Name.identifier("contextual")
          )
        )
        .first()

  fun generateContextResolvedBody() {
    irTransformFunctionBlockBodies { parent ->
      if (parent.hasAnnotation(ProofAnnotationsFqName.ContextResolvedAnnotation)) {
        val packageFqName = parent.getPackageFragment().fqName
        val name = parent.name
        val matchingFunctions = referenceFunctions(CallableId(packageFqName, name))
        val mirrorFunction =
          matchingFunctions
            .firstOrNull {
              it.owner.hasAnnotation(ProofAnnotationsFqName.ContextResolutionAnnotation)
            }
            ?.owner
        val body = createBlockBody(mirrorFunction?.body?.statements.orEmpty())
        val steps = buildProcessSteps(mirrorFunction)
        val functionBody =
          if (steps.isNotEmpty())
            processBodiesRecursive(parent, body as IrBlockBody, steps, null, emptyList(), steps.size)
          else body
        parent.body = functionBody
        println(parent.dump())
        parent
      } else null
    }
  }

  private fun buildProcessSteps(mirrorFunction: IrFunction?): List<ReceiverProcessStep> {
    val targetTypes: List<IrType> =
      mirrorFunction?.contextReceiversValueParameters.orEmpty().map { it.type }
    return targetTypes.map { type ->
      ReceiverProcessStep(contextualFunction.owner.irCall() as IrCall, type)
    }
  }

  data class ReceiverProcessStep(val replacementCall: IrCall, val type: IrType) {
    override fun toString(): String {
      return "step: ${type.dumpKotlinLike()}"
    }
  }

  private fun processBodiesRecursive(
    declarationParent: IrDeclarationParent,
    body: IrBlockBody,
    steps: List<ReceiverProcessStep>,
    previousStepLambda: IrFunctionExpression?,
    statements: List<IrStatement>,
    originalStepsSize: Int,
    paramSymbols: MutableMap<IrType, IrValueParameterSymbolImpl> = mutableMapOf()
  ): IrBody =
    when {
      steps.isEmpty() -> body // done processing
      else -> {
        val currentStep: ReceiverProcessStep = steps.first()
        val returningBlockType: IrType = declarationParent.returningBlockType()
        currentStep.replacementCall.addReplacedTypeArguments(currentStep.type, returningBlockType)
        val paramSymbol = IrValueParameterSymbolImpl()
        if (previousStepLambda != null) {
          val returned = previousStepLambda.function.createIrReturn(currentStep.replacementCall)
          val previousLambdaBody: IrBlockBody? = (previousStepLambda.function.body as? IrBlockBody)
          previousLambdaBody?.statements?.add(returned)
        }
        val nestedLambda =
          createLambdaExpressionWithoutParent(currentStep.type, returningBlockType, paramSymbol) {
            blockBody {}
          }
        nestedLambda.function.parent = declarationParent
        val extensionReceiverParam = nestedLambda.function.extensionReceiverParameter
        if (extensionReceiverParam != null)
          processContextReceiver(
            0,
            currentStep.type,
            currentStep.replacementCall,
            previousStepLambda?.function?.extensionReceiverParameter
          )
        currentStep.replacementCall.putValueArgument(1, nestedLambda)
        val lambdaBlockBody = nestedLambda.function.body
        // last processing nests the remaining
        if (lambdaBlockBody is IrBlockBody && (steps.size == 1 || originalStepsSize == 1)) {
          statements.forEach {
            val patchedStatement =
              if (it is IrReturn) nestedLambda.function.createIrReturn(it.value) else it
            lambdaBlockBody.statements.add(patchedStatement)
          }
        }
        val newReturn = declarationParent.createIrReturn(currentStep.replacementCall)
        val newStatements = listOf(newReturn)
        val transformedBody = createBlockBody(newStatements)
        paramSymbols[currentStep.type] = paramSymbol
        replaceErrorExpressionsWithReceiverValues(transformedBody, paramSymbols)
        val nextSteps = steps.drop(1)
        processBodiesRecursive(
          nestedLambda.function,
          transformedBody,
          nextSteps,
          nestedLambda,
          statements,
          originalStepsSize,
          paramSymbols
        )
      }
    }

  private fun createBlockBody(newStatements: List<IrStatement>): IrBlockBody =
    irFactory.createBlockBody(UNDEFINED_OFFSET, UNDEFINED_OFFSET, newStatements)

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

  private tailrec fun generateContextualCall(
    currentCall: IrCall?,
    mirrorBody: IrBody?,
    previousStepLambda: IrFunctionExpression?,
    declaration: IrFunction,
    contextReceivers: List<IrValueParameter>,
  ): IrCall? =
    when {
      contextReceivers.isEmpty() -> currentCall
      else -> {
        val call: IrCall = (contextualFunction.owner.irCall() as IrCall)
        val contextReceiver = contextReceivers.first()
        val remainingReceivers = contextReceivers.drop(1)
        // add current call to call
        val returningBlockType: IrType = declaration.returningBlockType()
        val paramSymbol = IrValueParameterSymbolImpl()

        call.addReplacedTypeArguments(contextReceiver.type, returningBlockType)

        if (previousStepLambda != null && currentCall != null) {
          val returned = previousStepLambda.function.createIrReturn(currentCall)
          val previousLambdaBody: IrBlockBody? = (previousStepLambda.function.body as? IrBlockBody)
          previousLambdaBody?.statements?.add(returned)
        }

        processContextReceiver(
          0,
          contextReceiver.type,
          call,
          previousStepLambda?.function?.extensionReceiverParameter
        )

        val nestedLambda =
          createLambdaExpressionWithoutParent(
            contextReceiver.type,
            returningBlockType,
            paramSymbol
          ) {
            blockBody {}
          }
        nestedLambda.function.parent = declaration
        call.putValueArgument(1, nestedLambda)
        val nestedLambdaBody = nestedLambda.function.body as IrBlockBody
        val returnCall = currentCall ?: call

        if (remainingReceivers.isEmpty()) {
          mirrorBody?.statements?.forEach {
            val patchedStatement =
              if (it is IrReturn) nestedLambda.function.createIrReturn(it.value) else it
            nestedLambdaBody.statements.add(patchedStatement)
          }
        }
        generateContextualCall(
          returnCall,
          mirrorBody,
          nestedLambda,
          declaration,
          remainingReceivers
        )
      }
    }

  private fun IrDeclarationParent.returningBlockType() =
    (this as? IrFunction)?.returnType ?: irBuiltIns.nothingType

  private fun createLambdaExpressionWithoutParent(
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

  private fun processContextReceiver(
    index: Int,
    irType: IrType?,
    replacementCall: IrMemberAccessExpression<*>?,
    receiverParam: IrValueParameter?
  ) {
    if (irType != null) {
      contextProofCall(irType)?.apply {
        if (this is IrFunctionAccessExpression) {
          symbol.owner.contextReceiversValueParameters.forEachIndexed { index, param ->
            val targetType = targetType(irType, param.type)
            val resolvedType = targetType ?: param.type
            processContextReceiver(index, resolvedType, this, param)
          }
        }
        if (replacementCall != null && replacementCall.valueArgumentsCount > index) {
          if (this is IrMemberAccessExpression<*>) {
            if (receiverParam != null && this.valueArgumentsCount > index) {
              val valueArg = receiverParam.irCall()
              this.putValueArgument(index, valueArg)
            }
          }
          replacementCall.putValueArgument(index, this)
        }
      }
    }
  }

  private fun contextProofCall(irType: IrType): IrExpression? =
    proofCache
      .getProofFromCache(
        irType.toIrBasedKotlinType().asProofCacheKey(ProofAnnotationsFqName.ContextualAnnotation)
      )
      ?.let { proofResolution -> substitutedResolvedContextCall(proofResolution, irType) }

  private fun substitutedResolvedContextCall(
    proofResolution: ProofResolution,
    irType: IrType
  ): IrExpression? {
    val proof = proofResolution.proof
    val ambiguousProofs = proofResolution.ambiguousProofs
    val internalProof =
      ambiguousProofs.firstOrNull {
        (it.declaration as? FirMemberDeclaration)?.visibility == Visibilities.Internal
      }
    return if (proof != null) substitutedContextProofCall(internalProof ?: proof, irType) else null
  }

  private fun substitutedContextProofCall(proof: Proof, irType: IrType): IrExpression {
    val proofIrDeclaration = proof.irDeclaration() as? IrFunction
    return matchedContextCandidateProofCall(
      declaration = proof.irDeclaration(),
      typeSubstitutor =
        IrTypeSubstitutor(
          proofIrDeclaration?.typeParameters.orEmpty().map { it.symbol },
          irType.getArguments().filterIsInstance<IrTypeArgument>(),
          irBuiltIns
        )
    )
  }

  private fun matchedContextCandidateProofCall(
    declaration: IrDeclaration,
    typeSubstitutor: IrTypeSubstitutor
  ): IrExpression {
    return declaration.irCall().apply {
      if (this is IrMemberAccessExpression<*>) {
        if (declaration is IrTypeParametersContainer) {
          declaration.typeParameters.forEachIndexed { index, typeParam ->
            val newType = typeSubstitutor.substitute(typeParam.defaultType)
            putTypeArgument(index, newType)
          }
        }

        if (declaration is IrFunction) {
          declaration.valueParameters.forEachIndexed { index, valueParameter ->
            val contextFqName: FqName? =
              valueParameter.metaContextAnnotations.firstOrNull()?.type?.classFqName
            if (contextFqName != null) {
              val newType = typeSubstitutor.substitute(valueParameter.type)
              val argumentProvedExpression = contextProofCall(newType)
              if (argumentProvedExpression != null) {
                putValueArgument(index, argumentProvedExpression)
              }
            }
          }
        }
      }
      this.type = typeSubstitutor.substitute(this.type)
    }
  }

  private fun irTransformFunctionBlockBodies(transformBody: (IrFunction) -> IrStatement?): Unit =
    moduleFragment.transformChildren(
      object : IrElementTransformer<Unit> {

        override fun visitFunction(declaration: IrFunction, data: Unit): IrStatement {
          return transformBody(declaration) ?: super.visitDeclaration(declaration, data)
        }
      },
      Unit
    )

  private fun IrCall.addReplacedTypeArguments(targetType: IrType, returningBlockType: IrType) {
    putTypeArgument(0, targetType)
    putTypeArgument(1, returningBlockType)
    type = returningBlockType
  }

  private fun IrDeclarationParent.createIrReturn(expression: IrExpression): IrReturn =
    IrReturnImpl(
      UNDEFINED_OFFSET,
      UNDEFINED_OFFSET,
      irBuiltIns.nothingType,
      (this as IrReturnTarget).symbol,
      expression
    )
}
