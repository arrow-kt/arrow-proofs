package arrow.inject.compiler.plugin.ir.utils

import arrow.inject.compiler.plugin.fir.utils.FirUtils
import arrow.inject.compiler.plugin.proof.Proof
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.ir.declarations.IrAnnotationContainer
import org.jetbrains.kotlin.ir.descriptors.toIrBasedKotlinType
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.types.model.KotlinTypeMarker

val IrAnnotationContainer.metaContextAnnotations: List<IrConstructorCall>
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
        .any { annotationDescriptor -> annotationDescriptor.fqName == FirUtils.ContextAnnotation }
    }

val IrAnnotationContainer.hasMetaContextAnnotation: Boolean
  get() = metaContextAnnotations.isNotEmpty()

fun KotlinTypeMarker.toIrType(): IrType = this as IrType
