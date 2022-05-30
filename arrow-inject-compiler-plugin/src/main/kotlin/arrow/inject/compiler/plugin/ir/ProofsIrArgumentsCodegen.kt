package arrow.inject.compiler.plugin.ir

import arrow.inject.compiler.plugin.fir.resolution.resolver.ProofCache
import arrow.inject.compiler.plugin.model.ProofAnnotationsFqName
import arrow.inject.compiler.plugin.model.ProofAnnotationsFqName.CompileTimeAnnotation
import arrow.inject.compiler.plugin.model.ProofAnnotationsFqName.InjectAnnotation
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrAnnotationContainer
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.descriptors.toIrBasedKotlinType
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionAccessExpression
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.IrTypeSystemContextImpl
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.isTypeParameter
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.ir.visitors.IrElementTransformer
import org.jetbrains.kotlin.types.model.KotlinTypeMarker
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
