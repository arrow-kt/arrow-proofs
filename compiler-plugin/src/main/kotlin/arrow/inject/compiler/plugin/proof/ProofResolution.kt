package arrow.inject.compiler.plugin.proof

import org.jetbrains.kotlin.fir.types.ConeKotlinType

data class ProofResolution(
  val proof: Proof?,
  val targetType: ConeKotlinType,
  val ambiguousProofs: List<Proof>
) {

  val isAmbiguous: Boolean
    get() = ambiguousProofs.size > 1 && proof != null
}
