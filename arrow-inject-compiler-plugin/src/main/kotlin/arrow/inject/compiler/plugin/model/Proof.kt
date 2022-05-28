package arrow.inject.compiler.plugin.model

import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.fir.declarations.FirClass
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirMemberDeclaration
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.FirSimpleFunction
import org.jetbrains.kotlin.fir.declarations.utils.visibility
import org.jetbrains.kotlin.fir.renderWithType
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.type
import org.jetbrains.kotlin.ir.util.IdSignature
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.SimpleType
import org.jetbrains.kotlin.types.model.KotlinTypeMarker

internal sealed class Proof {

  abstract val idSignature: IdSignature

  abstract val declaration: FirDeclaration

  fun declarationName(): String =
    when (val decl = declaration) {
      is FirSimpleFunction -> decl.name.asString()
      is FirProperty -> decl.name.asString()
      is FirClass -> decl.symbol.name.asString()
      else -> error("Unsupported declaration ${decl.renderWithType()}")
    }

  fun asString(): String =
    when (this) {
      is Implication -> "Proof.Implication: ${declaration.renderWithType()}}"
    }

  data class Implication(
    override val idSignature: IdSignature,
    override val declaration: FirDeclaration,
  ) : Proof()
}

internal data class ProofResolution(
  val proof: Proof?,
  val targetType: KotlinTypeMarker,
  val ambiguousProofs: List<Proof>,
) {

  val isAmbiguous: Boolean
    get() =
      when {
        ambiguousProofs.count() > 2 && proof != null -> true
        ambiguousProofs.count() == 2 &&
          (ambiguousProofs.all { it.isInternal } || ambiguousProofs.none { it.isInternal }) -> true
        else -> false
      }

  private val Proof.isInternal
    get() = (declaration as? FirMemberDeclaration)?.visibility == Visibilities.Internal
}

internal data class ProofCacheKey(
  val contextFqName: FqName?,
  val name: String,
  val typeArguments: List<ProofCacheKey>,
)

internal fun ConeKotlinType.asProofCacheKey(contextFqName: FqName?): ProofCacheKey =
  ProofCacheKey(
    contextFqName = contextFqName,
    name =
      when (this) {
        is ConeClassLikeType -> lookupTag.classId.asFqNameString()
        else -> error("Unsupported type")
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
