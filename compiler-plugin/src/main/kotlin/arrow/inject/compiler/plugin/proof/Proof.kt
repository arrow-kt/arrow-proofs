package arrow.inject.compiler.plugin.proof

import arrow.inject.compiler.plugin.fir.utils.isContextAnnotation
import java.util.concurrent.ConcurrentHashMap
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirAnonymousFunction
import org.jetbrains.kotlin.fir.declarations.FirAnonymousInitializer
import org.jetbrains.kotlin.fir.declarations.FirAnonymousObject
import org.jetbrains.kotlin.fir.declarations.FirBackingField
import org.jetbrains.kotlin.fir.declarations.FirConstructor
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirEnumEntry
import org.jetbrains.kotlin.fir.declarations.FirErrorFunction
import org.jetbrains.kotlin.fir.declarations.FirErrorProperty
import org.jetbrains.kotlin.fir.declarations.FirField
import org.jetbrains.kotlin.fir.declarations.FirFile
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.FirPropertyAccessor
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.FirSimpleFunction
import org.jetbrains.kotlin.fir.declarations.FirTypeAlias
import org.jetbrains.kotlin.fir.declarations.FirTypeParameter
import org.jetbrains.kotlin.fir.declarations.FirValueParameter
import org.jetbrains.kotlin.fir.declarations.FirVariable
import org.jetbrains.kotlin.fir.renderWithType
import org.jetbrains.kotlin.fir.resolve.fqName
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.type
import org.jetbrains.kotlin.ir.util.IdSignature
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.SimpleType

sealed class Proof {

  enum class DeclarationType {
    Function,
    Class,
    Object,
    Field,
    Variable,
    Property
  }

  abstract val idSignature: IdSignature

  abstract val declaration: FirDeclaration

  val declarationType: DeclarationType
    get() =
      when (declaration) {
        is FirConstructor -> DeclarationType.Function
        is FirFunction -> DeclarationType.Function
        is FirField -> DeclarationType.Field
        is FirProperty -> DeclarationType.Property
        is FirVariable ->DeclarationType.Variable
        is FirAnonymousObject -> DeclarationType.Object // TODO ?
        is FirRegularClass -> DeclarationType.Class
        else -> error("Unsupported declaration: $declaration")
      }

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

  data class Implication(
    override val idSignature: IdSignature,
    override val declaration: FirDeclaration,
  ) : Proof()
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

const val COMPILE_TIME_ANNOTATION = "arrow.inject.annotations.CompileTime"
const val CONTEXT_ANNOTATION = "arrow.inject.annotations.Context"
const val INJECT_ANNOTATION = "arrow.inject.annotations.Inject"
const val RESOLVE_ANNOTATION = "arrow.inject.annotations.Resolve"

fun ConeKotlinType.asProofCacheKey(contextFqName: FqName): ProofCacheKey =
  ProofCacheKey(
    contextFqName = contextFqName,
    name =
      when (this) {
        is ConeClassLikeType -> lookupTag.classId.asFqNameString()
        else -> TODO("Unsupported type")
      },
    typeArguments = typeArguments.mapNotNull { it.type?.asProofCacheKey(contextFqName) },
  )

fun KotlinType.asProofCacheKey(contextFqName: FqName): ProofCacheKey =
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

// fun KotlinTypeMarker.asProofCacheKey(contextFqName): ProofCacheKey =
//  when (this) {
//    is ConeKotlinType ->
//      ProofCacheKey(
//        contextFqName,
//        this.,
//        emptyMap(),
//      )
//    is arrow.inject.compiler.plugin.fir.utils.TypeArgument ->
//      ProofCacheKey(
//        contextFqName,
//        typeConstructor.fqName,
//        mapOf(
//          typeArgument.fqName to ProjectedType.TypeArgument,
//        ),
//      )
//    else -> throw IllegalArgumentException("Unknown type: $this")
//  }
