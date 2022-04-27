@file:OptIn(SessionConfiguration::class)

package arrow.inject.compiler.plugin.proof

import org.jetbrains.kotlin.fir.SessionConfiguration
import org.jetbrains.kotlin.types.model.KotlinTypeMarker

data class ProofResolution(
  val proof: Proof?,
  val targetType: KotlinTypeMarker,
  val ambiguousProofs: List<Proof>
) {

  val isAmbiguous: Boolean
    get() = ambiguousProofs.size > 1 && proof != null
}
