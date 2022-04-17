package arrow.inject.compiler.plugin.proof

import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.types.model.KotlinTypeMarker

sealed class Proof {

  data class Implication(
    val declaration: FirDeclaration,
    val type: KotlinTypeMarker,
  ) : Proof()
}

const val CONTEXT_ANNOTATION = "arrow.inject.annotations.Context"
const val INJECT_ANNOTATION =  "arrow.inject.annotations.Inject"
const val RESOLVE_ANNOTATION =  "arrow.inject.annotations.Resolve"
