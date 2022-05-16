package arrow.inject.compiler.plugin.fir.resolution

import arrow.inject.compiler.plugin.model.ProofCacheKey
import arrow.inject.compiler.plugin.model.ProofResolution
import java.util.concurrent.ConcurrentHashMap

class ProofCache {

  private val cache: ConcurrentHashMap<ProofCacheKey, ProofResolution> = ConcurrentHashMap()

  fun getProofFromCache(key: ProofCacheKey): ProofResolution? = cache[key]

  fun putProofIntoCache(key: ProofCacheKey, value: ProofResolution) {
    cache[key] = value
  }
}
