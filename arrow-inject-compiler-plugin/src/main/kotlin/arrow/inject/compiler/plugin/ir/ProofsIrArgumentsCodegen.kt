package arrow.inject.compiler.plugin.ir

import arrow.inject.compiler.plugin.fir.resolution.resolver.ProofCache
import arrow.inject.compiler.plugin.model.Proof
import arrow.inject.compiler.plugin.model.ProofAnnotationsFqName
import arrow.inject.compiler.plugin.model.ProofAnnotationsFqName.CompileTimeAnnotation
import arrow.inject.compiler.plugin.model.ProofAnnotationsFqName.InjectAnnotation
import arrow.inject.compiler.plugin.model.ProofResolution
import arrow.inject.compiler.plugin.model.asProofCacheKey
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.fir.declarations.FirMemberDeclaration
import org.jetbrains.kotlin.fir.declarations.utils.visibility
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrAnnotationContainer
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrTypeParametersContainer
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.descriptors.toIrBasedKotlinType
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionAccessExpression
import org.jetbrains.kotlin.ir.expressions.IrMemberAccessExpression
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.IrTypeSystemContextImpl
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.util.IdSignature
import org.jetbrains.kotlin.ir.util.getPackageFragment
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.isTypeParameter
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.ir.util.render
import org.jetbrains.kotlin.ir.visitors.IrElementTransformer
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.model.KotlinTypeMarker
import org.jetbrains.kotlin.types.model.TypeArgumentMarker
import org.jetbrains.kotlin.types.model.TypeSystemContext

internal class ProofsIrArgumentsCodegen(
  override val proofCache: ProofCache,
  override val moduleFragment: IrModuleFragment,
  private val irPluginContext: IrPluginContext
) :
  IrPluginContext by irPluginContext,
  TypeSystemContext by IrTypeSystemContextImpl(irPluginContext.irBuiltIns),
  ProofsIrAbstractCodegen {

  fun generateArguments() {
    irFunctionAccessExpression { call -> proveCall(call) }
  }

  private fun proveCall(call: IrFunctionAccessExpression): IrExpression =
    if (call.symbol.owner.annotations.hasAnnotation(InjectAnnotation)) insertGivenCall(call)
    else call

  private fun irFunctionAccessExpression(call: (IrFunctionAccessExpression) -> IrElement?): Unit =
    moduleFragment.transformChildren(
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

  fun insertGivenCall(call: IrFunctionAccessExpression): IrMemberAccessExpression<*> {
    val replacementCall: IrMemberAccessExpression<*> = givenReplacementCall(call)
    call.substitutedValueParameters.entries.forEachIndexed { index, (valueParameter, irType) ->
      processGivenValueParameter(index, valueParameter, irType, replacementCall)
    }
    return replacementCall
  }

  private fun processGivenValueParameter(
    index: Int,
    valueParameter: IrValueParameter,
    irType: IrType?,
    replacementCall: IrMemberAccessExpression<*>?
  ) {
    val contextFqName: FqName? =
      valueParameter.metaContextAnnotations.firstOrNull()?.type?.classFqName
    val type = irType?.toIrBasedKotlinType()
    if (contextFqName != null && type != null) {
      givenProofCall(contextFqName, type)?.apply {
        if (this is IrCall) {
          symbol.owner.explicitValueParameters.forEachIndexed { index, param ->
            val targetType = targetType(irType, param.type)
            val resolvedType = targetType ?: param.type
            processGivenValueParameter(index, param, resolvedType, this)
          }
        }
        if (replacementCall != null && replacementCall.valueArgumentsCount > index) {
          replacementCall.putValueArgument(index, this)
        }
      }
    }
  }

  fun givenProofCall(contextFqName: FqName, kotlinType: KotlinType): IrExpression? =
    proofCache.getProofFromCache(kotlinType.asProofCacheKey(contextFqName))?.let { proofResolution
      ->
      substitutedResolvedGivenCall(proofResolution, kotlinType)
    }

  private fun substitutedResolvedGivenCall(
    proofResolution: ProofResolution,
    kotlinType: KotlinType
  ): IrExpression? {
    val proof = proofResolution.proof
    val ambiguousProofs = proofResolution.ambiguousProofs
    val internalProof =
      ambiguousProofs.firstOrNull {
        (it.declaration as? FirMemberDeclaration)?.visibility == Visibilities.Internal
      }
    return if (proof != null) substitutedGivenProofCall(internalProof ?: proof, kotlinType) else null
  }

  private fun substitutedGivenProofCall(proof: Proof, kotlinType: KotlinType): IrExpression =
    matchedGivenCandidateProofCall(
      declaration = proof.irDeclaration(),
      typeArguments = proof.typeArgumentSubstitutor(kotlinType)
    )

  private fun matchedGivenCandidateProofCall(
    declaration: IrDeclaration,
    typeArguments: List<TypeArgumentMarker>
  ): IrExpression {
    val irTypes = declaration.substitutedIrTypes(typeArguments).filterNotNull()
    return declaration.irCall().apply {
      if (this is IrMemberAccessExpression<*>) {
        if (declaration is IrTypeParametersContainer) {
          declaration.typeParameters.forEachIndexed { index, _ ->
            putTypeArgument(index, irTypes.getOrElse(index) { irBuiltIns.nothingType })
          }
        }

        if (declaration is IrFunction) {
          declaration.valueParameters.forEachIndexed { index, valueParameter ->
            val contextFqName: FqName? =
              valueParameter.metaContextAnnotations.firstOrNull()?.type?.classFqName
            if (contextFqName != null) {
              val argumentProvedExpression =
                givenProofCall(
                  contextFqName,
                  irTypes.getOrElse(index) { irBuiltIns.nothingType }.toIrBasedKotlinType()
                )
              if (argumentProvedExpression != null) {
                putValueArgument(index, argumentProvedExpression)
              }
            }
          }
        }
      }
    }
  }

  private fun givenReplacementCall(irCall: IrFunctionAccessExpression): IrMemberAccessExpression<*> {
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

}

internal val IrFunctionAccessExpression.typeArguments: Map<Int, IrType?>
  get() {
    val arguments = arrayListOf<Pair<Int, IrType?>>()
    for (index in 0 until typeArgumentsCount) {
      arguments.add(index to getTypeArgument(index))
    }
    return arguments.toMap()
  }

internal val IrFunctionAccessExpression.valueArguments: Map<Int, IrExpression?>
  get() {
    val arguments = arrayListOf<Pair<Int, IrExpression?>>()
    for (index in 0 until valueArgumentsCount) {
      arguments.add(index to getValueArgument(index))
    }
    return arguments.toMap()
  }

internal val IrFunctionAccessExpression.substitutedValueParameters: Map<IrValueParameter, IrType?>
  get() = symbol.owner.substitutedValueParameters(this).toMap()

val IrFunction.explicitValueParameters: List<IrValueParameter>
  get() = valueParameters.subList(contextReceiverParametersCount, valueParameters.size)

internal fun IrFunction.substitutedValueParameters(
  call: IrFunctionAccessExpression
): List<Pair<IrValueParameter, IrType?>> =
  explicitValueParameters.map { substitutedTypeParameters(it, call) }

internal val IrAnnotationContainer.metaContextAnnotations: List<IrConstructorCall>
  get() =
    annotations.filter { irConstructorCall: IrConstructorCall ->
      irConstructorCall.type
        .toIrBasedKotlinType()
        .constructor.declarationDescriptor
        ?.annotations
        ?.toList()
        .orEmpty()
        .any { annotationDescriptor ->
          annotationDescriptor.fqName == ProofAnnotationsFqName.ContextAnnotation
        }
    }

internal fun KotlinTypeMarker.toIrType(): IrType = this as IrType

internal fun IrDeclaration.mirrorFunction(functionFqName: String): IrFunction? {
  return when (this) {
    is IrFunction -> {
      if (kotlinFqName.asString() == functionFqName &&
          !annotations.hasAnnotation(CompileTimeAnnotation)
      ) {
        this
      } else {
        null
      }
    }
    is IrClass -> {
      val mappedDeclarations = declarations.mapNotNull { it.mirrorFunction(functionFqName) }
      mappedDeclarations.firstOrNull()
    }
    else -> null
  }
}
