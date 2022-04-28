@file:OptIn(ObsoleteDescriptorBasedAPI::class)

package arrow.inject.compiler.plugin.ir

import arrow.inject.compiler.plugin.model.Proof
import arrow.inject.compiler.plugin.model.ProofAnnotationsFqName
import arrow.inject.compiler.plugin.model.ProofAnnotationsFqName.CompileTimeAnnotation
import arrow.inject.compiler.plugin.model.asProofCacheKey
import arrow.inject.compiler.plugin.model.getProofFromCache
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.fir.declarations.FirClass
import org.jetbrains.kotlin.fir.declarations.FirConstructor
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.FirVariable
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.ObsoleteDescriptorBasedAPI
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.declarations.IrAnnotationContainer
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrTypeParametersContainer
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.descriptors.toIrBasedKotlinType
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
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
import org.jetbrains.kotlin.ir.util.getPackageFragment
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.isTypeParameter
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.ir.util.render
import org.jetbrains.kotlin.ir.visitors.IrElementVisitor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.TypeUtils
import org.jetbrains.kotlin.types.model.KotlinTypeMarker
import org.jetbrains.kotlin.types.model.TypeArgumentMarker
import org.jetbrains.kotlin.types.model.TypeSystemContext
import org.jetbrains.kotlin.types.typeUtil.asTypeProjection

class ProofsIrCodegen(
  private val moduleFragment: IrModuleFragment,
  irPluginContext: IrPluginContext
) :
  IrPluginContext by irPluginContext,
  TypeSystemContext by IrTypeSystemContextImpl(irPluginContext.irBuiltIns) {

  fun proveCall(call: IrCall): IrMemberAccessExpression<*> =
    if (call.symbol.owner.annotations.hasAnnotation(CompileTimeAnnotation)) insertGivenCall(call)
    else call

  private fun insertGivenCall(call: IrCall): IrMemberAccessExpression<*> {
    val replacementCall: IrMemberAccessExpression<*> = replacementCall(call)
    call.substitutedValueParameters.forEachIndexed { index, (valueParameter, irType) ->
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
          symbol.owner.valueParameters.forEachIndexed { n, param ->
            processValueParameter(n, param, param.type, this)
          }
        }
        if (replacementCall != null && replacementCall.valueArgumentsCount > index)
          replacementCall.putValueArgument(index, this)
      }
    }
  }

  private fun givenProofCall(contextFqName: FqName, kotlinType: KotlinType): IrExpression? =
    getProofFromCache(kotlinType.asProofCacheKey(contextFqName))?.proof?.let { proof ->
      substitutedProofCall(proof, kotlinType)
    }

  private fun substitutedProofCall(proof: Proof, kotlinType: KotlinType): IrExpression =
    matchedCandidateProofCall(
      declaration = proof.irDeclaration(),
      typeSubstitutor = proof.substitutor(kotlinType)
    )

  private fun matchedCandidateProofCall(
    declaration: IrDeclaration,
    typeSubstitutor: List<TypeArgumentMarker>
  ): IrExpression {

    val irTypes = declaration.substitutedIrTypes(typeSubstitutor).filterNotNull()
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
              if (argumentProvedExpression != null) putValueArgument(index, argumentProvedExpression)
            }
          }
        }
      }
    }
  }

  private fun IrDeclaration.substitutedIrTypes(
    typeSubstitutor: List<TypeArgumentMarker>
  ): List<IrType?> =
    when (this) {
      is IrTypeParametersContainer -> {
        typeParameters.mapIndexed { _, typeParamDescriptor ->
          typeSubstitutor
            .find { key -> key.toString() == typeParamDescriptor.defaultType.toString() }
            ?.getType()
            ?.toIrType()
        }
      }
      else -> emptyList()
    }

  private fun Proof.substitutor(otherType: KotlinType): List<TypeArgumentMarker> {
    val allArgsMap =
      irDeclaration().type().typeArgumentsMap(otherType) +
        // ?.filter { it.key.type.isTypeParameter() } // TODO()
        mapOf(
          irBuiltIns.nothingType.toIrBasedKotlinType().asTypeProjection() to
            TypeUtils.DONT_CARE.asTypeProjection()
        )
    return allArgsMap.keys.toList()

    // TODO why this was using a map if we only use keys later?
    // return (typeSystemContext as TypeSystemInferenceExtensionContext)
    //   .typeSubstitutorByTypeConstructor(
    //     allArgsMap
    //       .map { (key, value) -> key.getType().typeConstructor() to value.getType() }
    //       .toMap()
    //   )
  }

  private fun KotlinTypeMarker.typeArgumentsMap(
    other: KotlinType
  ): Map<TypeArgumentMarker, TypeArgumentMarker> =
    if (isTypeVariableType()) mapOf(this.asTypeArgument() to other.asTypeArgument())
    else
      getArguments()
        .mapIndexed { n, typeProjection ->
          other.arguments.getOrNull(n)?.let { typeProjection to it }
        }
        .filterNotNull()
        .toMap()

  private fun IrDeclaration.irCall(): IrExpression =
    when (this) {
      is IrField -> {
        symbol.owner.correspondingPropertySymbol?.owner?.getter?.symbol?.let {
          irSimpleFunctionSymbol ->
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

  private fun replacementCall(irCall: IrCall): IrMemberAccessExpression<*> {
    val packageFqName = checkNotNull(irCall.symbol.owner.getPackageFragment()).fqName.asString()
    val functionFqName = checkNotNull(irCall.symbol.owner.kotlinFqName).asString()

    val signature = IdSignature.CommonSignature(packageFqName, functionFqName, null, 0)

    symbols.externalSymbolTable.referenceSimpleFunction(signature)

    val mirrorFunction: IrFunction? =
      moduleFragment.files.flatMap { it.declarations }.filterIsInstance<IrFunction>().firstOrNull {
        it.kotlinFqName.asString() == functionFqName &&
          !it.annotations.hasAnnotation(CompileTimeAnnotation)
      }

    checkNotNull(mirrorFunction) {
      "Expected mirror function for fake call ${irCall.render()} is null"
    }

    val rep: IrCall = mirrorFunction.symbol.owner.irCall() as IrCall
    // val rep: IrCall = irCall.deepCopyWithSymbols(mirrorFunction.symbol.owner)

    irCall.typeArguments.forEach { (n, arg) ->
      if (rep.typeArgumentsCount > n && arg != null) rep.putTypeArgument(n, arg)
    }
    irCall.valueArguments.forEach { (n, arg) ->
      if (rep.valueArgumentsCount > n && arg != null) rep.putValueArgument(n, arg)
    }

    // rep.extensionReceiver = this@replacementCall.extensionReceiver
    rep.dispatchReceiver = irCall.extensionReceiver
    return rep
  }

  fun Proof.irDeclaration(): IrDeclaration =
    when (declaration) {
      is FirClass -> symbolTable.referenceClass(idSignature).owner
      is FirConstructor -> symbolTable.referenceConstructor(idSignature).owner
      is FirFunction -> symbolTable.referenceSimpleFunction(idSignature).owner
      is FirVariable -> symbolTable.referenceField(idSignature).owner // TODO()
      else -> error("Unsupported FirDeclaration: $this")
    }

  fun IrDeclaration.type(): IrType =
    when (this) {
      is IrClass -> defaultType
      is IrFunction -> returnType
      is IrVariable -> type
      else -> error("Unsupported IrDeclaration: $this")
    }
}

private inline fun <reified E, B> IrElement.filterMap(
  crossinline filter: (E) -> Boolean,
  crossinline map: (E) -> B
): List<B> {
  val els = arrayListOf<B>()
  val visitor =
    object : IrElementVisitor<Unit, Unit> {
      override fun visitElement(element: IrElement, data: Unit) {
        if (element is E && filter(element)) {
          els.add(map(element))
        }
        element.acceptChildren(this, Unit)
      }
    }
  acceptChildren(visitor, Unit)
  return els
}

private val IrCall.typeArguments: List<Pair<Int, IrType?>>
  get() {
    val args = arrayListOf<Pair<Int, IrType?>>()
    for (i in 0 until typeArgumentsCount) {
      args.add(i to getTypeArgument(i))
    }
    return args.toList()
  }

private val IrCall.valueArguments: List<Pair<Int, IrExpression?>>
  get() {
    val args = arrayListOf<Pair<Int, IrExpression?>>()
    for (i in 0 until valueArgumentsCount) {
      args.add(i to getValueArgument(i))
    }
    return args.toList()
  }

private val IrCall.substitutedValueParameters: List<Pair<IrValueParameter, IrType?>>
  get() = symbol.owner.substitutedValueParameters(this)

private fun IrSimpleFunction.substitutedValueParameters(
  call: IrCall
): List<Pair<IrValueParameter, IrType?>> =
  valueParameters.map {
    val type = it.type
    it to
      (type.takeIf { t -> !t.isTypeParameter() }
        ?: typeParameters.firstOrNull { typeParam -> typeParam.defaultType == type }?.let {
          typeParam ->
          call.getTypeArgument(typeParam.index)
        }
          ?: type // Could not resolve the substituted KotlinType
      )
  }

private val IrAnnotationContainer.metaContextAnnotations: List<IrConstructorCall>
  get() =
    annotations.filter { irConstructorCall: IrConstructorCall ->
      irConstructorCall
        .type
        .toIrBasedKotlinType()
        .constructor
        .declarationDescriptor
        ?.annotations
        ?.toList()
        .orEmpty()
        .any { annotationDescriptor ->
          annotationDescriptor.fqName == ProofAnnotationsFqName.ContextAnnotation
        }
    }

private fun KotlinTypeMarker.toIrType(): IrType = this as IrType
