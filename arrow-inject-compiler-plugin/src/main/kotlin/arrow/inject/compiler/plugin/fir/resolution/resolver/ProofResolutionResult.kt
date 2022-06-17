package arrow.inject.compiler.plugin.fir.resolution.resolver

import arrow.inject.compiler.plugin.model.Proof
import org.jetbrains.kotlin.fir.resolve.calls.Candidate

internal sealed class ProofResolutionResult {
  data class Candidates(val candidates: Set<Candidate>) : ProofResolutionResult()
  data class CyclesFound(val proof: Proof) : ProofResolutionResult()
}
