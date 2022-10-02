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
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression
import org.jetbrains.kotlin.ir.expressions.IrMemberAccessExpression
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionExpressionImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrReturnImpl
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrValueParameterSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.IrTypeArgument
import org.jetbrains.kotlin.ir.types.IrTypeSubstitutor
import org.jetbrains.kotlin.ir.types.IrTypeSystemContextImpl
import org.jetbrains.kotlin.ir.types.addAnnotations
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.getPackageFragment
import org.jetbrains.kotlin.ir.util.hasAnnotation
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

  private val withFunction: IrSimpleFunctionSymbol
    get() =
      referenceFunctions(
          CallableId(
            packageName = FqName("kotlin"),
            className = null,
            callableName = Name.identifier("with")
          )
        )
        .first()

  fun generateContextResolvedBody() {
    irTransformFunctionBlockBodies { declaration ->
      if (declaration.hasAnnotation(ProofAnnotationsFqName.ContextResolvedAnnotation)) {
        val packageFqName = declaration.getPackageFragment().fqName
        val name = declaration.name
        val matchingFunctions = referenceFunctions(CallableId(packageFqName, name))
        val mirrorFunction =
          matchingFunctions.firstOrNull {
            it.owner.hasAnnotation(ProofAnnotationsFqName.ContextResolutionAnnotation)
          }
        val contextReceivers = mirrorFunction?.owner?.contextReceiversValueParameters.orEmpty()

        declaration
      } else null
    }
  }

  private fun generateWithCall(
    declaration: IrFunction,
    currentCall: IrCall?,
    contextReceivers: List<IrValueParameter>,
  ): IrCall? {
    return when {
      contextReceivers.isEmpty() -> currentCall
      else -> {
        val head = contextReceivers.first()
        val tail = contextReceivers.drop(1)
        val irType = head.type
        val replacementCall = (withFunction.owner.irCall() as IrCall)
        processContextReceiver(0, irType, replacementCall, null)
//        val nestedLambda =
//          createLambdaExpressionWithoutParent(currentStep.type, returningBlockType, paramSymbol) {
//            blockBody {
//
//            }.statements.add(declaration.createIrReturn(currentCall))
//          }

        generateWithCall(declaration, replacementCall, tail)
      }
    }
  }

  private fun IrDeclarationParent.returningBlockType() =
    (this as? IrFunction)?.returnType ?: irBuiltIns.nothingType

  private fun IrDeclarationParent.createIrReturn(expression: IrExpression): IrReturn =
    IrReturnImpl(
      UNDEFINED_OFFSET,
      UNDEFINED_OFFSET,
      irBuiltIns.nothingType,
      (this as IrReturnTarget).symbol,
      expression
    )

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

  private fun irTransformFunctionBlockBodies(
    processDeclaration: (IrFunction) -> IrStatement?
  ): Unit =
    moduleFragment.transformChildren(
      object : IrElementTransformer<Unit> {

        override fun visitFunction(declaration: IrFunction, data: Unit): IrStatement {
          return processDeclaration(declaration) ?: super.visitDeclaration(declaration, data)
        }
      },
      Unit
    )
}
