package arrow.inject.compiler.plugin.model

import org.jetbrains.kotlin.fir.extensions.AnnotationFqn

internal object ProofAnnotationsFqName {

  val ContextResolutionAnnotation = AnnotationFqn("arrow.inject.annotations.ContextResolution")

  val ContextualAnnotation = AnnotationFqn("arrow.inject.annotations.Contextual")

  val ContextualOverrideAnnotation = AnnotationFqn("arrow.inject.annotations.ContextualOverride")
}
