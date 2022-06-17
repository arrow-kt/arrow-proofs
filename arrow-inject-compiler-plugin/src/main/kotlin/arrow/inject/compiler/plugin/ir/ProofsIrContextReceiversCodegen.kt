package arrow.inject.compiler.plugin.ir

import arrow.inject.compiler.plugin.fir.resolution.resolver.ProofCache
import arrow.inject.compiler.plugin.model.Proof
import arrow.inject.compiler.plugin.model.ProofAnnotationsFqName.ProviderAnnotation
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
import org.jetbrains.kotlin.ir.declarations.IrDeclarationBase
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
import org.jetbrains.kotlin.ir.util.dumpKotlinLike
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.ir.util.statements
import org.jetbrains.kotlin.ir.visitors.IrElementTransformer
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
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
    irTransformBlockBodies { parent, body ->
      val steps = buildProcessSteps(body)
      if (steps.isNotEmpty())
        processBodiesRecursive(parent, body, steps, null, emptyList(), steps.size)
      else body
    }
  }

  data class ReceiverProcessStep(
    val contextCall: IrFunctionAccessExpression,
    val replacementCall: IrCall,
    val type: IrType
  ) {
    override fun toString(): String {
      return "step: ${type.dumpKotlinLike()}"
    }
  }

  fun buildProcessSteps(body: IrBlockBody): List<ReceiverProcessStep> {
    val contextCall = body.findNestedContextCall()
    return if (contextCall == null) emptyList()
    else {
      val targetTypes: List<IrType> =
        contextCall.typeArguments.values.flatMap {
          getAllContextReceiversTypes(it, mutableListOf())
        }
      targetTypes.map { type ->
        ReceiverProcessStep(contextCall, contextualFunction.owner.irCall() as IrCall, type)
      }
    }
  }

  fun processBodiesRecursive(
    declarationParent: IrDeclarationParent,
    body: IrBlockBody,
    steps: List<ReceiverProcessStep>,
    previousStepLambda: IrFunctionExpression?,
    remainingStatements: List<IrStatement>,
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
        if (
          lambdaBlockBody is IrBlockBody && steps.size == 1
        ) { // last processing nests the remaining
          remainingStatements.forEach {
            val patchedStatement =
              if (it is IrReturn) nestedLambda.function.createIrReturn(it.value) else it
            lambdaBlockBody.statements.add(patchedStatement)
          }
        }
//        if (originalStepsSize == 1) {
//          val returned = nestedLambda.function.createIrReturn(currentStep.replacementCall)
//          val lambdaBody: IrBlockBody? = (nestedLambda.function.body as? IrBlockBody)
//          lambdaBody?.statements?.add(returned)
//        }
        val statementsBeforeContext = body.statementsBeforeContextCall()
        val newReturn = declarationParent.createIrReturn(currentStep.replacementCall)
        val newStatements =
          if (steps.size == originalStepsSize) statementsBeforeContext + newReturn
          else statementsBeforeContext
        val transformedBody = createBlockBody(newStatements)
        paramSymbols[currentStep.type] = paramSymbol
        replaceErrorExpressionsWithReceiverValues(transformedBody, paramSymbols)
        // TODO nest body with other recursive function
        val nextSteps = steps.drop(1)
        val remaining = body.remainingStatementsAfterCall(currentStep.contextCall)
        transformedBody.statements.removeIf { it in remaining }
        processBodiesRecursive(
          nestedLambda.function,
          transformedBody,
          nextSteps,
          nestedLambda,
          remaining + remainingStatements,
          originalStepsSize,
          paramSymbols
        )
      }
    }

  private fun IrBlockBody.statementsBeforeContextCall() =
    statements.takeWhile { it.findNestedContextCall() == null }

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

  private fun IrDeclarationParent.returningBlockType() =
    (this as? IrFunction)?.returnType ?: irBuiltIns.nothingType

  private fun IrCall.addReplacedTypeArguments(targetType: IrType?, returningBlockType: IrType) {
    if (targetType != null) putTypeArgument(0, targetType)
    putTypeArgument(1, returningBlockType)
    type = returningBlockType
  }

  private fun IrElement.findNestedContextCall(): IrFunctionAccessExpression? =
    if (this is IrCall && isCallToContextSyntheticFunction) this
    else {
      var expression: IrFunctionAccessExpression? = null
      acceptChildrenVoid(
        object : IrElementVisitorVoid {
          override fun visitElement(element: IrElement) {
            if (element is IrCall && element.isCallToContextSyntheticFunction) {
              expression = element
            } else {
              element.acceptChildrenVoid(this)
            }
          }
        }
      )
      expression
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

  private fun IrDeclarationParent.createIrReturn(expression: IrExpression): IrReturn =
    IrReturnImpl(
      UNDEFINED_OFFSET,
      UNDEFINED_OFFSET,
      irBuiltIns.nothingType,
      (this as IrReturnTarget).symbol,
      expression
    )

  private fun getAllContextReceiversTypes(
    irType: IrType?,
    previousIrTypes: MutableList<IrType>,
  ): List<IrType> {
    if (irType != null) {
      previousIrTypes.add(irType)
      val expression = contextProofCall(irType)
      if (expression is IrCall) {
        expression.symbol.owner.contextReceiversValueParameters.flatMap { param ->
          val targetType = targetType(irType, param.type)
          val resolvedType = targetType ?: param.type
          getAllContextReceiversTypes(resolvedType, previousIrTypes)
        }
      }
    }
    return previousIrTypes.reversed()
  }

  private fun processContextReceiver(
    index: Int,
    irType: IrType?,
    replacementCall: IrMemberAccessExpression<*>?,
    receiverParam: IrValueParameter?
  ) {
    if (irType != null) {
      contextProofCall(irType)?.apply {
        if (this is IrCall) {
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
      .getProofFromCache(irType.toIrBasedKotlinType().asProofCacheKey(ProviderAnnotation))
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

  private val IrFunctionAccessExpression.isCallToContextSyntheticFunction
    get() = symbol.owner.kotlinFqName == FqName("arrow.inject.annotations.context")

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

val IrFunction.contextReceiversValueParameters: List<IrValueParameter>
  get() = valueParameters.subList(0, contextReceiverParametersCount)
