package arrow.inject.compiler.plugin.proof

import org.jetbrains.kotlin.types.model.KotlinTypeMarker

data class ProofResolution(
  val proof: Proof?,
  val targetType: KotlinTypeMarker,
  val ambiguousProofs: List<Proof>
)
