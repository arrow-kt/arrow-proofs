package arrow.inject.compiler.plugin.model

import org.jetbrains.kotlin.fir.extensions.AnnotationFqn

internal object ProofAnnotationsFqName {

  val CompileTimeAnnotation = AnnotationFqn("arrow.inject.annotations.CompileTime")

  val ContextAnnotation = AnnotationFqn("arrow.inject.annotations.Context")

  val ProviderAnnotation = AnnotationFqn("arrow.inject.annotations.Provider")
}
