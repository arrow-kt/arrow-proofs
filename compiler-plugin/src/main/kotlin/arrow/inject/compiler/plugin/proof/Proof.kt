package arrow.inject.compiler.plugin.proof

import arrow.inject.compiler.plugin.fir.utils.isContextAnnotation
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.renderWithType
import org.jetbrains.kotlin.fir.resolve.fqName
import org.jetbrains.kotlin.name.FqName

sealed class Proof {

  abstract val declaration: FirDeclaration

  fun asString(): String =
    when (this) {
      is Implication -> "Proof.Implication: ${declaration.renderWithType()}}"
    }

  fun contexts(session: FirSession): Set<FqName> =
    declaration
      .annotations
      .filter { it.isContextAnnotation(session) }
      .mapNotNull { it.fqName(session) }
      .toSet()

  data class Implication(override val declaration: FirDeclaration) : Proof()
}

const val COMPILE_TIME_ANNOTATION = "arrow.inject.annotations.CompileTime"
const val CONTEXT_ANNOTATION = "arrow.inject.annotations.Context"
const val INJECT_ANNOTATION = "arrow.inject.annotations.Inject"
const val RESOLVE_ANNOTATION = "arrow.inject.annotations.Resolve"
