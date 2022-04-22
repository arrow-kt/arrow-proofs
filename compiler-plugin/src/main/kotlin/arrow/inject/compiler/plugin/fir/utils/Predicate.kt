package arrow.inject.compiler.plugin.fir.utils

import org.jetbrains.kotlin.fir.extensions.predicate.has
import org.jetbrains.kotlin.fir.extensions.predicate.metaHas
import org.jetbrains.kotlin.fir.extensions.predicate.metaUnder

object Predicate {
  val CONTEXT_PREDICATE = has(FirUtils.ContextAnnotation)
  val META_CONTEXT_PREDICATE = metaUnder(FirUtils.ContextAnnotation)
  val INJECT_PREDICATE = has(FirUtils.InjectAnnotation)
  val RESOLVE_PREDICATE = has(FirUtils.ResolveAnnotation)
}
