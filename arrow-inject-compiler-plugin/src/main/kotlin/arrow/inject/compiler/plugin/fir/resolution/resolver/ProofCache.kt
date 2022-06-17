package arrow.inject.compiler.plugin.fir.resolution.resolver

import arrow.inject.compiler.plugin.model.ProofCacheKey
import arrow.inject.compiler.plugin.model.ProofResolution
import java.util.concurrent.ConcurrentHashMap

public class ProofCache {

  private val cache: ConcurrentHashMap<ProofCacheKey, ProofResolution> = ConcurrentHashMap()

  internal fun getProofFromCache(key: ProofCacheKey): ProofResolution? = cache[key]

  internal fun putProofIntoCache(key: ProofCacheKey, value: ProofResolution) {
    cache[key] = value
  }
}
