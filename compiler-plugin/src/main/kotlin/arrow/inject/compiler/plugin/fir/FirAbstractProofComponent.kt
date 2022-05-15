@file:OptIn(SymbolInternals::class)

package arrow.inject.compiler.plugin.fir

import arrow.inject.compiler.plugin.fir.collectors.ExternalProofCollector
import arrow.inject.compiler.plugin.fir.collectors.LocalProofCollectors
import arrow.inject.compiler.plugin.model.Proof
import arrow.inject.compiler.plugin.model.ProofAnnotationsFqName
import org.jetbrains.kotlin.fir.FirAnnotationContainer
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.toClassLikeSymbol
import org.jetbrains.kotlin.fir.analysis.checkers.toRegularClassSymbol
import org.jetbrains.kotlin.fir.backend.Fir2IrSignatureComposer
import org.jetbrains.kotlin.fir.backend.jvm.FirJvmKotlinMangler
import org.jetbrains.kotlin.fir.declarations.FirClass
import org.jetbrains.kotlin.fir.declarations.FirConstructor
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.FirSimpleFunction
import org.jetbrains.kotlin.fir.declarations.FirValueParameter
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.extensions.predicate.DeclarationPredicate
import org.jetbrains.kotlin.fir.extensions.predicate.has
import org.jetbrains.kotlin.fir.resolve.calls.Candidate
import org.jetbrains.kotlin.fir.resolve.defaultType
import org.jetbrains.kotlin.fir.resolve.fqName
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.signaturer.FirBasedSignatureComposer
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirTypeParameterSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.builder.buildResolvedTypeRef
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.type
import org.jetbrains.kotlin.ir.util.IdSignature
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

internal interface FirAbstractProofComponent {

  val session: FirSession

  val allCollectedProofs: List<Proof>
    get() =
      localProofCollector.collectLocalProofs() + externalProofCollector.collectExternalProofs()

  val localProofCollector: LocalProofCollectors
    get() = LocalProofCollectors(session)

  val externalProofCollector: ExternalProofCollector
    get() = ExternalProofCollector(session)

  val composer: Fir2IrSignatureComposer
    get() = FirBasedSignatureComposer(FirJvmKotlinMangler(session))

  val contextPredicate: DeclarationPredicate
    get() = has(ProofAnnotationsFqName.ContextAnnotation)

  val injectPredicate: DeclarationPredicate
    get() = has(ProofAnnotationsFqName.InjectAnnotation)

  val resolvePredicate: DeclarationPredicate
    get() = has(ProofAnnotationsFqName.ResolveAnnotation)

  val FirDeclaration.idSignature: IdSignature
    get() = checkNotNull(composer.composeSignature(this))

  val resolve: FirSimpleFunction
    get() =
      session.symbolProvider
        .getTopLevelCallableSymbols(FqName("arrow.inject.annotations"), Name.identifier("resolve"))
        .first()
        .fir as FirSimpleFunction

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

  val FirDeclaration.coneType: ConeKotlinType
    get() =
      when (this) {
        is FirFunction -> returnTypeRef.coneType
        is FirProperty -> returnTypeRef.coneType
        is FirRegularClass -> symbol.defaultType()
        is FirValueParameter -> returnTypeRef.coneType
        else -> error("Unsupported FirDeclaration: $this")
      }

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

  private fun typeArgIndex(
    typeArgs: List<FirTypeParameterSymbol>,
    expressionType: ConeKotlinType
  ) = typeArgs.indexOfFirst { it.name.asString() == expressionType.toString() }

  fun typeArgs(type: ConeKotlinType) =
    type.toRegularClassSymbol(session)?.typeParameterSymbols.orEmpty()

  fun targetType(
    type: ConeKotlinType,
    expressionType: ConeKotlinType
  ): ConeKotlinType? {
    val typeArgs = typeArgs(type)
    val typeArgIndex = typeArgIndex(typeArgs, expressionType)
    val targetType = if (typeArgIndex >= 0) type.typeArguments[typeArgIndex].type else null
    return targetType
  }

  fun targetTypeRef(
    type: ConeKotlinType,
    expressionType: ConeKotlinType
  ): FirResolvedTypeRef {
    val targetType = targetType(type, expressionType)

    val typeRef = if (targetType != null) buildResolvedTypeRef {
      this.type = targetType
    } else buildResolvedTypeRef {
      this.type = expressionType
    }
    return typeRef
  }
}
