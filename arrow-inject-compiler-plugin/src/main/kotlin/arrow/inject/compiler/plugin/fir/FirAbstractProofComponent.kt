package arrow.inject.compiler.plugin.fir

import arrow.inject.compiler.plugin.model.ProofAnnotationsFqName
import org.jetbrains.kotlin.descriptors.EffectiveVisibility
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.fir.FirAnnotationContainer
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.toClassLikeSymbol
import org.jetbrains.kotlin.fir.declarations.FirClass
import org.jetbrains.kotlin.fir.declarations.FirConstructor
import org.jetbrains.kotlin.fir.declarations.FirContextReceiver
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirDeclarationAttributes
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.FirResolvePhase
import org.jetbrains.kotlin.fir.declarations.FirSimpleFunction
import org.jetbrains.kotlin.fir.declarations.FirValueParameter
import org.jetbrains.kotlin.fir.declarations.builder.buildSimpleFunction
import org.jetbrains.kotlin.fir.declarations.builder.buildTypeParameter
import org.jetbrains.kotlin.fir.declarations.impl.FirResolvedDeclarationStatusImpl
import org.jetbrains.kotlin.fir.declarations.origin
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.extensions.predicate.DeclarationPredicate
import org.jetbrains.kotlin.fir.extensions.predicate.annotated
import org.jetbrains.kotlin.fir.extensions.predicate.metaAnnotated
import org.jetbrains.kotlin.fir.extensions.predicate.or
import org.jetbrains.kotlin.fir.moduleData
import org.jetbrains.kotlin.fir.resolve.defaultType
import org.jetbrains.kotlin.fir.resolve.fqName
import org.jetbrains.kotlin.fir.symbols.ConeTypeParameterLookupTag
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirTypeParameterSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.builder.buildResolvedTypeRef
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.impl.ConeTypeParameterTypeImpl
import org.jetbrains.kotlin.fir.types.toRegularClassSymbol
import org.jetbrains.kotlin.fir.types.type
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.types.Variance

internal interface FirAbstractProofComponent {

  val session: FirSession

  val contextPredicate: DeclarationPredicate
    get() =
      annotated(ProofAnnotationsFqName.ContextAnnotation)
        .or(metaAnnotated(ProofAnnotationsFqName.ContextAnnotation))

  val injectPredicate: DeclarationPredicate
    get() = annotated(ProofAnnotationsFqName.InjectAnnotation)

  val resolve: FirSimpleFunction
    get() {
      val typeArg = buildTypeParameter {
        moduleData = session.moduleData
        resolvePhase = FirResolvePhase.RAW_FIR
        origin = ProofKey.origin
        attributes = FirDeclarationAttributes()
        name = Name.identifier("A")
        symbol = FirTypeParameterSymbol()
        containingDeclarationSymbol = resolveOriginalSymbol
        variance = Variance.OUT_VARIANCE
        isReified = false
      }
      return buildSimpleFunction {
        resolvePhase = FirResolvePhase.BODY_RESOLVE
        moduleData = session.moduleData
        origin = ProofKey.origin
        status =
          FirResolvedDeclarationStatusImpl(
            Visibilities.Public,
            Modality.FINAL,
            EffectiveVisibility.Public,
          )
        returnTypeRef = buildResolvedTypeRef {
          this.type = ConeTypeParameterTypeImpl(ConeTypeParameterLookupTag(typeArg.symbol), false)
        }
        name = resolveCallableId.callableName
        symbol = resolveOriginalSymbol
        typeParameters += listOf(typeArg)
      }
    }

  val FirAnnotation.isContextAnnotation: Boolean
    get() {
      val annotations: List<FirAnnotation> =
        typeRef.toClassLikeSymbol(session)?.annotations.orEmpty()
      return annotations.any { it.fqName(session) == ProofAnnotationsFqName.ContextAnnotation }
    }

  val FirAnnotationContainer.metaContextAnnotations: List<FirAnnotation>
    get() =
      annotations.filter { firAnnotation ->
        firAnnotation.typeRef.toClassLikeSymbol(session)?.annotations.orEmpty().any {
          it.fqName(session) == ProofAnnotationsFqName.ContextAnnotation
        }
      }

  val FirAnnotationContainer.hasMetaContextAnnotation: Boolean
    get() = metaContextAnnotations.isNotEmpty()

  val FirDeclaration.boundedTypes: Map<FqName, ConeKotlinType>
    get() =
      when (this) {
        is FirFunction -> {
          valueParameters
            .filter { it.hasMetaContextAnnotation }
            .associate { it.contextFqNames.first() to it.returnTypeRef.coneType }
        }
        is FirClass -> {
          declarations
            .filterIsInstance<FirConstructor>()
            .flatMap { constructor ->
              constructor.valueParameters
                .filter { it.hasMetaContextAnnotation }
                .flatMap { parameter -> parameter.boundedTypes.toList() }
            }
            .toMap()
        }
        is FirValueParameter -> {
          mapOf(this.contextFqNames.first() to returnTypeRef.coneType)
        }
        else -> {
          emptyMap()
        }
      }

  val FirDeclaration.contextFqNames: Set<FqName>
    get() = annotations.filter { it.isContextAnnotation }.mapNotNull { it.fqName(session) }.toSet()

  private fun typeArgIndex(typeArgs: List<FirTypeParameterSymbol>, expressionType: ConeKotlinType) =
    typeArgs.indexOfFirst { it.name.asString() == expressionType.toString() }

  fun typeArgs(type: ConeKotlinType) =
    type.toRegularClassSymbol(session)?.typeParameterSymbols.orEmpty()

  fun targetType(type: ConeKotlinType, expressionType: ConeKotlinType): ConeKotlinType? {
    val typeArgs = typeArgs(type)
    val typeArgIndex = typeArgIndex(typeArgs, expressionType)
    return if (typeArgIndex >= 0) type.typeArguments[typeArgIndex].type else null
  }

  fun targetTypeRef(type: ConeKotlinType, expressionType: ConeKotlinType): FirResolvedTypeRef {
    val targetType = targetType(type, expressionType)

    return if (targetType != null) buildResolvedTypeRef { this.type = targetType }
    else buildResolvedTypeRef { this.type = expressionType }
  }
}

private val resolveCallableId =
  CallableId(FqName("arrow.inject.annotations"), Name.identifier("resolve"))

private val resolveOriginalSymbol = FirNamedFunctionSymbol(resolveCallableId)

val FirDeclaration.coneType: ConeKotlinType
  get() =
    when (this) {
      is FirFunction -> returnTypeRef.coneType
      is FirProperty -> returnTypeRef.coneType
      is FirRegularClass -> symbol.defaultType()
      is FirValueParameter -> returnTypeRef.coneType
      else -> error("Unsupported FirDeclaration: $this")
    }

val FirDeclaration.contextReceivers: List<FirContextReceiver>
  get() =
    when (this) {
      is FirFunction -> contextReceivers
      is FirRegularClass -> contextReceivers
      is FirProperty -> contextReceivers
      else -> emptyList()
    }
