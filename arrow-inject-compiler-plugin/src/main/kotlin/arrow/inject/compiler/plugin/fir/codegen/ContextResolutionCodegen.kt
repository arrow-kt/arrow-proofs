package arrow.inject.compiler.plugin.fir.codegen

import arrow.inject.compiler.plugin.fir.FirAbstractProofComponent
import arrow.inject.compiler.plugin.fir.FirResolutionProof
import arrow.inject.compiler.plugin.fir.ProofKey
import arrow.inject.compiler.plugin.fir.collectors.ExternalProofCollector
import arrow.inject.compiler.plugin.fir.collectors.LocalProofCollectors
import arrow.inject.compiler.plugin.fir.resolution.resolver.ProofCache
import arrow.inject.compiler.plugin.model.Proof
import arrow.inject.compiler.plugin.model.ProofAnnotationsFqName
import org.jetbrains.kotlin.descriptors.Modality
import java.util.concurrent.atomic.AtomicInteger
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.toClassLikeSymbol
import org.jetbrains.kotlin.fir.caches.FirLazyValue
import org.jetbrains.kotlin.fir.caches.firCachesFactory
import org.jetbrains.kotlin.fir.declarations.FirDeclarationAttributes
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.declarations.FirResolvePhase
import org.jetbrains.kotlin.fir.declarations.FirResolvedDeclarationStatus
import org.jetbrains.kotlin.fir.declarations.FirTypeParameter
import org.jetbrains.kotlin.fir.declarations.FirValueParameter
import org.jetbrains.kotlin.fir.declarations.builder.buildSimpleFunction
import org.jetbrains.kotlin.fir.declarations.builder.buildTypeParameter
import org.jetbrains.kotlin.fir.declarations.builder.buildValueParameter
import org.jetbrains.kotlin.fir.declarations.impl.FirResolvedDeclarationStatusImpl
import org.jetbrains.kotlin.fir.declarations.origin
import org.jetbrains.kotlin.fir.declarations.utils.effectiveVisibility
import org.jetbrains.kotlin.fir.declarations.utils.modality
import org.jetbrains.kotlin.fir.declarations.utils.visibility
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.expressions.buildResolvedArgumentList
import org.jetbrains.kotlin.fir.expressions.builder.FirBlockBuilder
import org.jetbrains.kotlin.fir.expressions.builder.buildAnnotation
import org.jetbrains.kotlin.fir.expressions.builder.buildBlock
import org.jetbrains.kotlin.fir.expressions.builder.buildFunctionCall
import org.jetbrains.kotlin.fir.expressions.impl.FirEmptyAnnotationArgumentMapping
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.MemberGenerationContext
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.moduleData
import org.jetbrains.kotlin.fir.originalForSubstitutionOverrideAttr
import org.jetbrains.kotlin.fir.references.builder.buildResolvedNamedReference
import org.jetbrains.kotlin.fir.resolve.constructType
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirTypeParameterSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirValueParameterSymbol
import org.jetbrains.kotlin.fir.types.ConeLookupTagBasedType
import org.jetbrains.kotlin.fir.types.builder.buildResolvedTypeRef
import org.jetbrains.kotlin.fir.types.builder.buildTypeProjectionWithVariance
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.util.collectionUtils.filterIsInstanceAnd

internal class ContextResolutionCodegen(
  override val proofCache: ProofCache,
  session: FirSession,
) : FirDeclarationGenerationExtension(session), FirAbstractProofComponent, FirResolutionProof {

  override val allFirLazyProofs: FirLazyValue<List<Proof>, Unit> =
    session.firCachesFactory.createLazyValue {
      LocalProofCollectors(session).collectLocalProofs() +
        ExternalProofCollector(session).collectExternalProofs()
    }

  private val counter: AtomicInteger = AtomicInteger(0)

  private val unitSymbol: FirClassLikeSymbol<*>
    get() = session.builtinTypes.unitType.toClassLikeSymbol(session)!!

  private val contextResolvedAnnotationClassId: ClassId
    get() =
      ClassId.fromString(
        ProofAnnotationsFqName.ContextResolvedAnnotation.asString().replace(".", "/")
      )

  private val contextResolvedAnnotationClassLikeSymbol: FirClassLikeSymbol<*>
    get() =
      checkNotNull(
        session.symbolProvider.getClassLikeSymbolByClassId(contextResolvedAnnotationClassId)
      ) {
        // TODO: rename this artifact if it is wrong before publishing the final release
        "@CompileTime annotation is missing, add io.arrow-kt.arrow-inject-annotations"
      }

  private val contextResolvedAnnotationType: ConeLookupTagBasedType
    get() = contextResolvedAnnotationClassLikeSymbol.fir.symbol.constructType(emptyArray(), false)

  override fun FirDeclarationPredicateRegistrar.registerPredicates() {
    register(contextResolutionPredicate)
  }

  override fun generateFunctions(
    callableId: CallableId,
    context: MemberGenerationContext?
  ): List<FirNamedFunctionSymbol> =
    session.predicateBasedProvider
      .getSymbolsByPredicate(contextResolutionPredicate)
      .filterIsInstanceAnd<FirNamedFunctionSymbol> { it.callableId == callableId }
      .map { firNamedFunctionSymbol ->
        buildContextResolutionFunctionSymbol(firNamedFunctionSymbol, callableId)
      }

  override fun getTopLevelCallableIds(): Set<CallableId> =
    session.predicateBasedProvider
      .getSymbolsByPredicate(contextResolutionPredicate)
      .filterIsInstance<FirNamedFunctionSymbol>()
      .map { it.callableId }
      .toSet()

  override fun hasPackage(packageFqName: FqName): Boolean = true

  private fun buildContextResolutionFunctionSymbol(
    firNamedFunctionSymbol: FirNamedFunctionSymbol,
    callableId: CallableId
  ): FirNamedFunctionSymbol {
    val originalSymbol = FirNamedFunctionSymbol(callableId)
    return buildSimpleFunction {
        //contextReceivers.clear()
        resolvePhase = FirResolvePhase.BODY_RESOLVE
        moduleData = firNamedFunctionSymbol.moduleData
        origin = ProofKey.origin

//        body = buildBlock {
//        }

        status = FirResolvedDeclarationStatusImpl(
          firNamedFunctionSymbol.fir.visibility,
          firNamedFunctionSymbol.fir.modality ?: Modality.FINAL,
          firNamedFunctionSymbol.fir.effectiveVisibility
        )
        returnTypeRef = firNamedFunctionSymbol.resolvedReturnTypeRef
        name = callableId.callableName
        symbol = originalSymbol
        dispatchReceiverType = firNamedFunctionSymbol.dispatchReceiverType
        annotations += buildAnnotations()
        typeParameters += buildTypeParameters(firNamedFunctionSymbol, originalSymbol)
        valueParameters +=
          buildValueParameters(firNamedFunctionSymbol) /*+ unambiguousUnitValueParameter()*/
      }
      .apply {
       // this.originalForSubstitutionOverrideAttr = firNamedFunctionSymbol.fir
       // this.or
      }
      .symbol
  }

  private fun unambiguousUnitValueParameter() = buildValueParameter {
    val newName = Name.identifier(generateFreshUnitName())
    moduleData = session.moduleData
    resolvePhase = FirResolvePhase.BODY_RESOLVE
    origin = ProofKey.origin
    returnTypeRef = session.builtinTypes.unitType
    this.name = newName
    symbol = FirValueParameterSymbol(newName)
    isCrossinline = false
    isNoinline = false
    isVararg = false
    defaultValue = buildFunctionCall {
      typeRef = session.builtinTypes.unitType
      argumentList = buildResolvedArgumentList(LinkedHashMap())
      calleeReference = buildResolvedNamedReference {
        this.name = unitSymbol.name
        resolvedSymbol = unitSymbol
      }
    }
  }

  private fun generateFreshUnitName() = "_unit${counter.getAndIncrement()}_"

  private fun buildAnnotations(): FirAnnotation = buildAnnotation {
    annotationTypeRef = buildResolvedTypeRef { type = contextResolvedAnnotationType }
    argumentMapping = FirEmptyAnnotationArgumentMapping
  }

  private fun buildStatus(): FirResolvedDeclarationStatus =
    FirResolvedDeclarationStatusImpl.DEFAULT_STATUS_FOR_STATUSLESS_DECLARATIONS

  private fun buildTypeParameters(
    firNamedFunctionSymbol: FirFunctionSymbol<*>,
    originalSymbol: FirFunctionSymbol<*>
  ): List<FirTypeParameter> =
    firNamedFunctionSymbol.typeParameterSymbols.map { typeParameter ->
      buildTypeParameter {
        moduleData = session.moduleData
        resolvePhase = FirResolvePhase.RAW_FIR
        origin = ProofKey.origin
        attributes = FirDeclarationAttributes()
        name = typeParameter.name
        symbol = FirTypeParameterSymbol()
        containingDeclarationSymbol = originalSymbol
        variance = typeParameter.variance
        isReified = typeParameter.isReified
        bounds += typeParameter.resolvedBounds
        annotations += typeParameter.annotations
      }
    }

  private fun buildValueParameters(
    firFunctionSymbol: FirFunctionSymbol<*>
  ): List<FirValueParameter> =
    firFunctionSymbol.valueParameterSymbols.map { valueParameter ->
      buildValueParameter {
        moduleData = session.moduleData
        resolvePhase = FirResolvePhase.RAW_FIR
        origin = ProofKey.origin
        attributes = valueParameter.fir.attributes
        returnTypeRef = valueParameter.fir.returnTypeRef
        containerSource = valueParameter.fir.containerSource
        dispatchReceiverType = valueParameter.fir.dispatchReceiverType
        contextReceivers += valueParameter.fir.contextReceivers
        name = valueParameter.fir.name
        backingField = valueParameter.fir.backingField
        symbol = FirValueParameterSymbol(valueParameter.fir.name)
        annotations +=
          valueParameter.annotations.map { firAnnotation ->
            buildAnnotation {
              annotationTypeRef = buildResolvedTypeRef {
                type = firAnnotation.annotationTypeRef.coneType
              }
              argumentMapping = FirEmptyAnnotationArgumentMapping
            }
          }
        defaultValue =
          if (valueParameter.fir.hasMetaContextAnnotation) {
            buildFunctionCall {
              typeRef = valueParameter.resolvedReturnTypeRef
              argumentList = buildResolvedArgumentList(LinkedHashMap())
              typeArguments +=
                valueParameter.typeParameterSymbols.map {
                  buildTypeProjectionWithVariance {
                    typeRef = valueParameter.resolvedReturnTypeRef
                    variance = Variance.OUT_VARIANCE
                  }
                }
              calleeReference = buildResolvedNamedReference {
                name = resolve.name
                resolvedSymbol = resolve.symbol
              }
            }
          } else {
            valueParameter.fir.defaultValue
          }
        isCrossinline = valueParameter.fir.isCrossinline
        isNoinline = valueParameter.fir.isNoinline
        isVararg = valueParameter.fir.isVararg
      }
    }
}
