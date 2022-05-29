@file:OptIn(SymbolInternals::class)

package arrow.inject.compiler.plugin.fir

import arrow.inject.compiler.plugin.fir.collectors.ExternalProofCollector
import arrow.inject.compiler.plugin.fir.collectors.LocalProofCollectors
import arrow.inject.compiler.plugin.fir.resolution.resolver.ProofCache
import arrow.inject.compiler.plugin.fir.resolution.resolver.ProofResolutionResult
import arrow.inject.compiler.plugin.fir.resolution.resolver.ProofResolutionStageRunner
import arrow.inject.compiler.plugin.model.Proof
import arrow.inject.compiler.plugin.model.ProofResolution
import arrow.inject.compiler.plugin.model.asProofCacheKey
import org.jetbrains.kotlin.fir.resolve.calls.Candidate
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.name.FqName

internal interface FirResolutionProof : FirProofIdSignature {

  val proofCache: ProofCache

  val allProofs: List<Proof>

  val proofResolutionStageRunner: ProofResolutionStageRunner
    get() = ProofResolutionStageRunner(session, this)

  val allCollectedProofs: List<Proof>
    get() =
      localProofCollector.collectLocalProofs() + externalProofCollector.collectExternalProofs()

  val localProofCollector: LocalProofCollectors
    get() = LocalProofCollectors(session)

  val externalProofCollector: ExternalProofCollector
    get() = ExternalProofCollector(session)

  fun resolveProof(
    contextFqName: FqName,
    type: ConeKotlinType,
    currentType: ConeKotlinType?
  ): ProofResolution {
    return when (val resolutionResult = resolutionResult(contextFqName, type, currentType)) {
      is ProofResolutionResult.Candidates -> {
        val candidates = resolutionResult.candidates
        val proofResolution = proofCandidate(candidates, type, resolutionResult)
        return proofResolution.apply {
          proofCache.putProofIntoCache(type.asProofCacheKey(contextFqName), this)
        }
      }
      is ProofResolutionResult.CyclesFound -> {
        ProofResolution(resolutionResult.proof, type, emptyList(), resolutionResult)
      }
    }
  }

  private fun resolutionResult(
    contextFqName: FqName,
    type: ConeKotlinType,
    currentType: ConeKotlinType?,
  ): ProofResolutionResult =
    proofResolutionStageRunner.run {
      allProofs
        .filter { contextFqName in it.declaration.contextFqNames }
        .matchingCandidates(type, currentType)
    }

  private fun proofCandidate(
    candidates: Set<Candidate>,
    type: ConeKotlinType,
    resolutionResult: ProofResolutionResult.Candidates,
  ): ProofResolution {
    val candidate: Candidate? = candidates.firstOrNull()

    return ProofResolution(
      proof = candidate?.asProof(),
      targetType = type,
      ambiguousProofs =
        candidates.map { Proof.Implication(it.symbol.fir.idSignature, it.symbol.fir) },
      result = resolutionResult
    )
  }

  fun Candidate.asProof(): Proof = Proof.Implication(symbol.fir.idSignature, symbol.fir)
}
