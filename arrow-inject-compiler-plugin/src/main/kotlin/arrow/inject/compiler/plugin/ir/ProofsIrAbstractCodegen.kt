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
import org.jetbrains.kotlin.fir.declarations.utils.classId
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.descriptors.toIrBasedKotlinType
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstructorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetObjectValueImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetValueImpl
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.dumpKotlinLike
import org.jetbrains.kotlin.types.model.TypeSystemContext

interface ProofsIrAbstractCodegen : IrPluginContext, TypeSystemContext {

  val moduleFragment: IrModuleFragment

  val proofCache: ProofCache

  private fun typeArgIndex(typeArgs: List<TypeParameterDescriptor>, expressionType: IrType) =
    typeArgs.indexOfFirst { it.name.asString() == expressionType.dumpKotlinLike() }

  private fun typeArgs(type: IrType): List<TypeParameterDescriptor> =
    (type.toIrBasedKotlinType().constructor.declarationDescriptor as? ClassDescriptor)
      ?.declaredTypeParameters
      .orEmpty()

  fun targetType(type: IrType, expressionType: IrType): IrType? {
    val typeArgs = typeArgs(type)
    val typeArgIndex = typeArgIndex(typeArgs, expressionType)
    return if (typeArgIndex >= 0) type.getArgument(typeArgIndex) as? IrType else null
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
      is IrValueParameter -> {
        IrGetValueImpl(
          startOffset = UNDEFINED_OFFSET,
          endOffset = UNDEFINED_OFFSET,
          type = symbol.owner.type,
          symbol = symbol
        )
      }
      else -> {
        TODO("Unsupported ir call for $this")
      }
    }

  // TODO: show better errors when null
  fun Proof.irDeclaration(): IrDeclaration =
    when (val declaration = declaration) {
      is FirClass -> referenceClass(declaration.classId)!!.constructors.first().owner
      is FirConstructor ->
        referenceConstructors(declaration.symbol.callableId.classId!!)
          .first { it.owner.isPrimary }
          .owner
      is FirFunction -> referenceFunctions(declaration.symbol.callableId).first().owner
      is FirProperty -> referenceProperties(declaration.symbol.callableId).first().owner
      else -> error("Unsupported FirDeclaration: $this")
    }
}
