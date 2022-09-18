package arrow.inject.compiler.plugin.fir.codegen

import arrow.inject.compiler.plugin.fir.FirAbstractProofComponent
import arrow.inject.compiler.plugin.fir.FirResolutionProof
import arrow.inject.compiler.plugin.fir.ProofKey
import arrow.inject.compiler.plugin.fir.resolution.resolver.ProofCache
import arrow.inject.compiler.plugin.model.Proof
import arrow.inject.compiler.plugin.model.ProofAnnotationsFqName
import java.util.concurrent.atomic.AtomicInteger
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.toClassLikeSymbol
import org.jetbrains.kotlin.fir.containingClassForStaticMemberAttr
import org.jetbrains.kotlin.fir.declarations.FirDeclarationAttributes
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.declarations.FirResolvePhase
import org.jetbrains.kotlin.fir.declarations.FirResolvedDeclarationStatus
import org.jetbrains.kotlin.fir.declarations.FirTypeParameter
import org.jetbrains.kotlin.fir.declarations.FirValueParameter
import org.jetbrains.kotlin.fir.declarations.builder.buildConstructor
import org.jetbrains.kotlin.fir.declarations.builder.buildSimpleFunction
import org.jetbrains.kotlin.fir.declarations.builder.buildTypeParameter
import org.jetbrains.kotlin.fir.declarations.builder.buildValueParameter
import org.jetbrains.kotlin.fir.declarations.impl.FirResolvedDeclarationStatusImpl
import org.jetbrains.kotlin.fir.declarations.origin
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.expressions.buildResolvedArgumentList
import org.jetbrains.kotlin.fir.expressions.builder.buildAnnotation
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
import org.jetbrains.kotlin.fir.symbols.impl.ConeClassLikeLookupTagImpl
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
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
import org.jetbrains.kotlin.name.SpecialNames
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.util.collectionUtils.filterIsInstanceAnd

internal class ResolvedFunctionGenerationExtension(
  override val proofCache: ProofCache,
  session: FirSession,
) : FirDeclarationGenerationExtension(session), FirAbstractProofComponent, FirResolutionProof {

  override val allProofs: List<Proof> by lazy { allCollectedProofs }

  private val counter: AtomicInteger = AtomicInteger(0)

  private val unitSymbol: FirClassLikeSymbol<*>
    get() = session.builtinTypes.unitType.toClassLikeSymbol(session)!!

  private val compileTimeAnnotationClassId: ClassId
    get() =
      ClassId.fromString(ProofAnnotationsFqName.CompileTimeAnnotation.asString().replace(".", "/"))

  private val compileTimeAnnotationClassLikeSymbol: FirClassLikeSymbol<*>
    get() =
      checkNotNull(
        session.symbolProvider.getClassLikeSymbolByClassId(compileTimeAnnotationClassId)
      ) {
        // TODO: rename this artifact if it is wrong before publishing the final release
        "@CompileTime annotation is missing, add io.arrow-kt.arrow-inject-annotations"
      }

  private val compileTimeAnnotationType: ConeLookupTagBasedType
    get() = compileTimeAnnotationClassLikeSymbol.fir.symbol.constructType(emptyArray(), false)

  override fun FirDeclarationPredicateRegistrar.registerPredicates() {
    register(contextPredicate)
    register(injectPredicate)
  }

  override fun generateConstructors(context: MemberGenerationContext): List<FirConstructorSymbol> =
    session.predicateBasedProvider
      .getSymbolsByPredicate(injectPredicate)
      .filterIsInstanceAnd<FirConstructorSymbol> { it.callableId.classId == context.owner.classId }
      .map { firConstructorSymbol -> buildConstructorSymbol(context.owner, firConstructorSymbol) }

  override fun generateFunctions(
    callableId: CallableId,
    context: MemberGenerationContext?
  ): List<FirNamedFunctionSymbol> =
    session.predicateBasedProvider
      .getSymbolsByPredicate(injectPredicate)
      .filterIsInstanceAnd<FirNamedFunctionSymbol> { it.callableId == callableId }
      .map { firNamedFunctionSymbol ->
        buildInjectableFunctionSymbol(firNamedFunctionSymbol, callableId)
      }

  override fun getCallableNamesForClass(classSymbol: FirClassSymbol<*>): Set<Name> =
    (localProofCollector.collectLocalInjectables() +
        externalProofCollector.collectExternalInjectables())
      .filter { it.classId == classSymbol.classId }
      .map {
        if (it.callableName == it.className?.shortName()) SpecialNames.INIT else it.callableName
      }
      .toSet()

  override fun getTopLevelCallableIds(): Set<CallableId> =
    localProofCollector.collectLocalInjectables() +
      externalProofCollector.collectExternalInjectables()

  override fun hasPackage(packageFqName: FqName): Boolean = true

  private fun buildConstructorSymbol(
    owner: FirClassSymbol<*>,
    firConstructorSymbol: FirConstructorSymbol
  ): FirConstructorSymbol {
    val originalSymbol = FirConstructorSymbol(owner.classId)
    return buildConstructor {
        resolvePhase = FirResolvePhase.RAW_FIR
        moduleData = firConstructorSymbol.moduleData
        origin = FirDeclarationOrigin.SubstitutionOverride
        status = buildStatus()
        returnTypeRef = firConstructorSymbol.resolvedReturnTypeRef
        symbol = originalSymbol
        dispatchReceiverType = firConstructorSymbol.dispatchReceiverType
        annotations += buildAnnotations()
        typeParameters += buildTypeParameters(firConstructorSymbol, originalSymbol)
        valueParameters +=
          buildValueParameters(firConstructorSymbol) + unambiguousUnitValueParameter()
      }
      .apply {
        this.containingClassForStaticMemberAttr = ConeClassLikeLookupTagImpl(owner.classId)
        this.originalForSubstitutionOverrideAttr = firConstructorSymbol.fir
      }
      .symbol
  }

  private fun buildInjectableFunctionSymbol(
    firNamedFunctionSymbol: FirNamedFunctionSymbol,
    callableId: CallableId
  ): FirNamedFunctionSymbol {
    val originalSymbol = FirNamedFunctionSymbol(callableId)
    return buildSimpleFunction {
        resolvePhase = FirResolvePhase.BODY_RESOLVE
        moduleData = firNamedFunctionSymbol.moduleData
        origin = FirDeclarationOrigin.SubstitutionOverride
        status = buildStatus()
        returnTypeRef = firNamedFunctionSymbol.resolvedReturnTypeRef
        name = callableId.callableName
        symbol = originalSymbol
        dispatchReceiverType = firNamedFunctionSymbol.dispatchReceiverType
        annotations += buildAnnotations()
        typeParameters += buildTypeParameters(firNamedFunctionSymbol, originalSymbol)
        valueParameters +=
          buildValueParameters(firNamedFunctionSymbol) + unambiguousUnitValueParameter()
      }
      .apply { this.originalForSubstitutionOverrideAttr = firNamedFunctionSymbol.fir }
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
    annotationTypeRef = buildResolvedTypeRef { type = compileTimeAnnotationType }
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
