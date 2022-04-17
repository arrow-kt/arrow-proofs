package arrow.inject.compiler.plugin.fir.utils

import arrow.inject.compiler.plugin.proof.CONTEXT_ANNOTATION
import arrow.inject.compiler.plugin.proof.INJECT_ANNOTATION
import arrow.inject.compiler.plugin.proof.RESOLVE_ANNOTATION
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.extensions.AnnotationFqn
import org.jetbrains.kotlin.fir.extensions.predicate.DeclarationPredicate
import org.jetbrains.kotlin.fir.extensions.predicate.has
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.resolve.fqName
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol

interface FirUtils {

  val session: FirSession

  fun FirClassSymbol<*>.hasContextAnnotation() =
    annotations.any { it.fqName(session) == ContextAnnotation }

  fun renderHasContextAnnotation() =
    println(session.predicateBasedProvider.getSymbolsByPredicate(DeclarationPredicate.Any))

  companion object {
    val ContextAnnotation = AnnotationFqn(CONTEXT_ANNOTATION)
    val InjectAnnotation = AnnotationFqn(INJECT_ANNOTATION)
    val ResolveAnnotation = AnnotationFqn(RESOLVE_ANNOTATION)
  }
}
