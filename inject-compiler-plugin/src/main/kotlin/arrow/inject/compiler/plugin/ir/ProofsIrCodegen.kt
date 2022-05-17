@file:OptIn(ObsoleteDescriptorBasedAPI::class)

package arrow.inject.compiler.plugin.ir

import arrow.inject.compiler.plugin.fir.resolution.resolver.ProofCache
import arrow.inject.compiler.plugin.model.Proof
import arrow.inject.compiler.plugin.model.ProofAnnotationsFqName
import arrow.inject.compiler.plugin.model.ProofAnnotationsFqName.CompileTimeAnnotation
import arrow.inject.compiler.plugin.model.ProofAnnotationsFqName.InjectAnnotation
import arrow.inject.compiler.plugin.model.asProofCacheKey
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.TypeParameterDescriptor
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.fir.declarations.FirClass
import org.jetbrains.kotlin.fir.declarations.FirConstructor
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.FirMemberDeclaration
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.utils.visibility
import org.jetbrains.kotlin.ir.ObsoleteDescriptorBasedAPI
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.declarations.IrAnnotationContainer
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrTypeParametersContainer
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.descriptors.toIrBasedKotlinType
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionAccessExpression
import org.jetbrains.kotlin.ir.expressions.IrMemberAccessExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstructorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetObjectValueImpl
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.IrTypeSystemContextImpl
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.util.IdSignature
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.dumpKotlinLike
import org.jetbrains.kotlin.ir.util.getPackageFragment
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.isTypeParameter
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.ir.util.render
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.model.KotlinTypeMarker
import org.jetbrains.kotlin.types.model.TypeArgumentMarker
import org.jetbrains.kotlin.types.model.TypeSystemContext
import org.jetbrains.kotlin.types.typeUtil.asTypeProjection

class ProofsIrCodegen(
  private val proofCache: ProofCache,
  private val moduleFragment: IrModuleFragment,
  irPluginContext: IrPluginContext
) :
  IrPluginContext by irPluginContext,
  TypeSystemContext by IrTypeSystemContextImpl(irPluginContext.irBuiltIns) {

  fun proveCall(call: IrFunctionAccessExpression): IrMemberAccessExpression<*> =
    if (call.symbol.owner.annotations.hasAnnotation(InjectAnnotation)) insertGivenCall(call)
    else call

  private fun insertGivenCall(call: IrFunctionAccessExpression): IrMemberAccessExpression<*> {
    val replacementCall: IrMemberAccessExpression<*> = replacementCall(call)
    call.substitutedValueParameters.entries.forEachIndexed { index, (valueParameter, irType) ->
      processValueParameter(index, valueParameter, irType, replacementCall)
    }
    return replacementCall
  }

  private fun processValueParameter(
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
          symbol.owner.valueParameters.forEachIndexed { index, param ->
            val targetType = targetType(irType, param.type)
            val resolvedType = targetType ?: param.type
            processValueParameter(index, param, resolvedType, this)
          }
        }
        if (replacementCall != null && replacementCall.valueArgumentsCount > index) {
          replacementCall.putValueArgument(index, this)
        }
      }
    }
  }

  private fun typeArgIndex(typeArgs: List<TypeParameterDescriptor>, expressionType: IrType) =
    typeArgs.indexOfFirst { it.name.asString() == expressionType.dumpKotlinLike() }

  private fun typeArgs(type: IrType): List<TypeParameterDescriptor> =
    (type.toIrBasedKotlinType().constructor.declarationDescriptor as? ClassDescriptor)
      ?.declaredTypeParameters.orEmpty()

  private fun targetType(type: IrType, expressionType: IrType): IrType? {
    val typeArgs = typeArgs(type)
    val typeArgIndex = typeArgIndex(typeArgs, expressionType)
    return if (typeArgIndex >= 0) type.getArgument(typeArgIndex) as? IrType else null
  }

  private fun givenProofCall(contextFqName: FqName, kotlinType: KotlinType): IrExpression? =
    proofCache.getProofFromCache(kotlinType.asProofCacheKey(contextFqName))?.let { proofResolution
      ->
      val proof = proofResolution.proof
      val ambiguousProofs = proofResolution.ambiguousProofs
      val internalProof =
        ambiguousProofs.firstOrNull {
          (it.declaration as? FirMemberDeclaration)?.visibility == Visibilities.Internal
        }
      if (proof != null) substitutedProofCall(internalProof ?: proof, kotlinType) else null
    }

  private fun substitutedProofCall(proof: Proof, kotlinType: KotlinType): IrExpression =
    matchedCandidateProofCall(
      declaration = proof.irDeclaration(),
      typeArguments = proof.typeArgumentSubstitutor(kotlinType)
    )

  private fun matchedCandidateProofCall(
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

  private fun IrDeclaration.substitutedIrTypes(
    typeArguments: List<TypeArgumentMarker>
  ): List<IrType?> =
    when (this) {
      is IrTypeParametersContainer -> {
        typeParameters.map { irTypeParameter ->
          typeArguments
            .find { key -> key.toString() == irTypeParameter.defaultType.toString() }
            ?.getType()
            ?.toIrType()
        }
      }
      else -> emptyList()
    }

  private fun Proof.typeArgumentSubstitutor(otherType: KotlinType): List<TypeArgumentMarker> {
    return irDeclaration().type().typeArguments(otherType) +
      irBuiltIns.nothingType.toIrBasedKotlinType().asTypeProjection()

    // TODO why this was using a map if we only use keys later?
    // return (typeSystemContext as TypeSystemInferenceExtensionContext)
    //   .typeSubstitutorByTypeConstructor(
    //     allArgsMap
    //       .map { (key, value) -> key.getType().typeConstructor() to value.getType() }
    //       .toMap()
    //   )
  }

  private fun KotlinTypeMarker.typeArguments(other: KotlinType): List<TypeArgumentMarker> =
    if (isTypeVariableType()) {
      listOf(asTypeArgument())
    } else {
      getArguments()
        .mapIndexed { index, typeArgumentMarker ->
          other.arguments.getOrNull(index)?.let { typeArgumentMarker }
        }
        .filterNotNull()
    }

  private fun IrDeclaration.irCall(): IrExpression =
    when (this) {
      is IrProperty -> {
        symbol.owner.getter?.symbol?.let { irSimpleFunctionSymbol ->
          IrCallImpl(
            startOffset = UNDEFINED_OFFSET,
            endOffset = UNDEFINED_OFFSET,
            type = irSimpleFunctionSymbol.owner.returnType,
            symbol = irSimpleFunctionSymbol,
            typeArgumentsCount = irSimpleFunctionSymbol.owner.typeParameters.size,
            valueArgumentsCount = irSimpleFunctionSymbol.owner.valueParameters.size
          )
        }
          ?: TODO("Unsupported irCall for $this")
      }
      is IrConstructor -> {
        IrConstructorCallImpl(
          startOffset = UNDEFINED_OFFSET,
          endOffset = UNDEFINED_OFFSET,
          type = symbol.owner.returnType,
          symbol = symbol,
          typeArgumentsCount = symbol.owner.typeParameters.size,
          valueArgumentsCount = symbol.owner.valueParameters.size,
          constructorTypeArgumentsCount = symbol.owner.typeParameters.size
        )
      }
      is IrFunction -> {
        IrCallImpl(
          startOffset = UNDEFINED_OFFSET,
          endOffset = UNDEFINED_OFFSET,
          type = symbol.owner.returnType,
          symbol = symbol as IrSimpleFunctionSymbol,
          typeArgumentsCount = symbol.owner.typeParameters.size,
          valueArgumentsCount = symbol.owner.valueParameters.size
        )
      }
      is IrClass -> {
        IrGetObjectValueImpl(
          startOffset = UNDEFINED_OFFSET,
          endOffset = UNDEFINED_OFFSET,
          type = symbol.owner.defaultType,
          symbol = symbol
        )
      }
      else -> {
        TODO("Unsupported ir call for $this")
      }
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
      is FirClass -> symbolTable.referenceClass(idSignature).owner
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

private val IrFunctionAccessExpression.typeArguments: Map<Int, IrType?>
  get() {
    val arguments = arrayListOf<Pair<Int, IrType?>>()
    for (index in 0 until typeArgumentsCount) {
      arguments.add(index to getTypeArgument(index))
    }
    return arguments.toMap()
  }

private val IrFunctionAccessExpression.valueArguments: Map<Int, IrExpression?>
  get() {
    val arguments = arrayListOf<Pair<Int, IrExpression?>>()
    for (index in 0 until valueArgumentsCount) {
      arguments.add(index to getValueArgument(index))
    }
    return arguments.toMap()
  }

private val IrFunctionAccessExpression.substitutedValueParameters: Map<IrValueParameter, IrType?>
  get() = symbol.owner.substitutedValueParameters(this).toMap()

private fun IrFunction.substitutedValueParameters(
  call: IrFunctionAccessExpression
): List<Pair<IrValueParameter, IrType?>> =
  valueParameters.map {
    val type = it.type
    it to
      (type.takeIf { t -> !t.isTypeParameter() }
        ?: typeParameters
          .firstOrNull { typeParam -> typeParam.defaultType == type }
          ?.let { typeParam -> call.getTypeArgument(typeParam.index) }
          ?: type // Could not resolve the substituted KotlinType
      )
  }

private val IrAnnotationContainer.metaContextAnnotations: List<IrConstructorCall>
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

private fun KotlinTypeMarker.toIrType(): IrType = this as IrType

private fun IrDeclaration.mirrorFunction(functionFqName: String): IrFunction? {
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
