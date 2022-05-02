package arrow.inject.compiler.plugin.model

import java.util.concurrent.ConcurrentHashMap
import org.jetbrains.kotlin.fir.declarations.FirClass
import org.jetbrains.kotlin.fir.declarations.FirConstructor
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.FirValueParameter
import org.jetbrains.kotlin.fir.renderWithType
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.type
import org.jetbrains.kotlin.ir.util.IdSignature
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.SimpleType
import org.jetbrains.kotlin.types.model.KotlinTypeMarker

sealed class Proof {

  abstract val idSignature: IdSignature

  abstract val declaration: FirDeclaration

  fun asString(): String =
    when (this) {
      is Implication -> "Proof.Implication: ${declaration.renderWithType()}}"
    }

  data class Implication(
    override val idSignature: IdSignature,
    override val declaration: FirDeclaration,
  ) : Proof()
}

data class ProofResolution(
  val proof: Proof?,
  val targetType: KotlinTypeMarker,
  val ambiguousProofs: List<Proof>
) {

  val isAmbiguous: Boolean
    get() = ambiguousProofs.isNotEmpty() && proof != null
}

val cache: ConcurrentHashMap<ProofCacheKey, ProofResolution> = ConcurrentHashMap()

fun getProofFromCache(key: ProofCacheKey): ProofResolution? = cache[key]

fun putProofIntoCache(key: ProofCacheKey, value: ProofResolution) {
  cache[key] = value
}

data class ProofCacheKey(
  val contextFqName: FqName,
  val name: String,
  val typeArguments: List<ProofCacheKey>,
)

internal fun ConeKotlinType.asProofCacheKey(contextFqName: FqName): ProofCacheKey =
  ProofCacheKey(
    contextFqName = contextFqName,
    name =
      when (this) {
        is ConeClassLikeType -> lookupTag.classId.asFqNameString()
        else -> TODO("Unsupported type")
      },
    typeArguments = typeArguments.mapNotNull { it.type?.asProofCacheKey(contextFqName) },
  )

internal fun KotlinType.asProofCacheKey(contextFqName: FqName): ProofCacheKey =
  ProofCacheKey(
    contextFqName = contextFqName,
    name =
      when (this) {
        is SimpleType -> {
          this.constructor.declarationDescriptor?.fqNameSafe?.asString()
            ?: error("Unsupported type")
        }
        else -> error("Unsupported type")
      },
    typeArguments = arguments.map { it.type.asProofCacheKey(contextFqName) },
  )
