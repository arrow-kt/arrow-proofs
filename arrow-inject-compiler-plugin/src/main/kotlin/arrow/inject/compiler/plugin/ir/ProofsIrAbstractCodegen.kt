package arrow.inject.compiler.plugin.ir

import arrow.inject.compiler.plugin.fir.resolution.resolver.ProofCache
import arrow.inject.compiler.plugin.model.Proof
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.TypeParameterDescriptor
import org.jetbrains.kotlin.fir.declarations.FirClass
import org.jetbrains.kotlin.fir.declarations.FirConstructor
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrTypeParametersContainer
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.descriptors.toIrBasedKotlinType
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionAccessExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstructorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetObjectValueImpl
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.dumpKotlinLike
import org.jetbrains.kotlin.ir.util.isTypeParameter
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.model.KotlinTypeMarker
import org.jetbrains.kotlin.types.model.TypeArgumentMarker
import org.jetbrains.kotlin.types.model.TypeSystemContext
import org.jetbrains.kotlin.types.typeUtil.asTypeProjection

interface ProofsIrAbstractCodegen : IrPluginContext, TypeSystemContext {

  val moduleFragment: IrModuleFragment

  val proofCache: ProofCache

  private fun typeArgIndex(typeArgs: List<TypeParameterDescriptor>, expressionType: IrType) =
    typeArgs.indexOfFirst { it.name.asString() == expressionType.dumpKotlinLike() }

  private fun typeArgs(type: IrType): List<TypeParameterDescriptor> =
    (type.toIrBasedKotlinType().constructor.declarationDescriptor as? ClassDescriptor)
      ?.declaredTypeParameters.orEmpty()

  fun targetType(type: IrType, expressionType: IrType): IrType? {
    val typeArgs = typeArgs(type)
    val typeArgIndex = typeArgIndex(typeArgs, expressionType)
    return if (typeArgIndex >= 0) type.getArgument(typeArgIndex) as? IrType else null
  }

  fun IrDeclaration.substitutedIrTypes(
    typeArguments: List<TypeArgumentMarker>
  ): List<IrType?> =
    when (this) {
      is IrTypeParametersContainer -> {
        typeParameters.map { irTypeParameter ->
          typeArguments
            .find {
              it == irTypeParameter.defaultType
            }
            ?.getType()
            ?.toIrType()
        }
      }
      else -> emptyList()
    }

  fun Proof.typeArgumentSubstitutor(otherType: KotlinType): List<TypeArgumentMarker> {
    return irDeclaration().type().typeArguments(otherType)
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

  fun IrDeclaration.irCall(): IrExpression =
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

  // TODO: Fix init
  private fun Proof.irCallableDeclaration(): IrDeclaration =
    when (declaration) {
      is FirClass -> symbolTable.referenceClass(idSignature).constructors.first().owner
      is FirConstructor -> symbolTable.referenceConstructor(idSignature).owner
      is FirFunction -> symbolTable.referenceSimpleFunction(idSignature).owner
      is FirProperty -> symbolTable.referenceProperty(idSignature).owner
      else -> error("Unsupported FirDeclaration: $this")
    }

  fun Proof.irDeclaration(): IrDeclaration =
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

fun IrFunction.substitutedTypeParameters(
  it: IrValueParameter,
  call: IrFunctionAccessExpression
): Pair<IrValueParameter, IrType> {
  val type = it.type
  return it to
    (type.takeIf { t -> !t.isTypeParameter() }
      ?: typeParameters
        .firstOrNull { typeParam -> typeParam.defaultType == type }
        ?.let { typeParam -> call.getTypeArgument(typeParam.index) }
      ?: type // Could not resolve the substituted KotlinType
      )
}
