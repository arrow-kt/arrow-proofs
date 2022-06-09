package arrow.inject.compiler.plugin.model

import org.jetbrains.kotlin.fir.extensions.AnnotationFqn

internal object ProofAnnotationsFqName {

  val CompileTimeAnnotation = AnnotationFqn("arrow.inject.annotations.CompileTime")

  val ContextAnnotation = AnnotationFqn("arrow.inject.annotations.Context")

  val InjectAnnotation = AnnotationFqn("arrow.inject.annotations.Inject")

  val ResolveAnnotation = AnnotationFqn("arrow.inject.annotations.Resolve")

  val ProviderAnnotation = AnnotationFqn("arrow.inject.annotations.Provider")
}
