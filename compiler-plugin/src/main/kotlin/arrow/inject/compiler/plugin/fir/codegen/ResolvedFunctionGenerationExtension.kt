@file:OptIn(SymbolInternals::class)

package arrow.inject.compiler.plugin.fir.codegen

import arrow.inject.compiler.plugin.fir.FirAbstractProofComponent
import arrow.inject.compiler.plugin.fir.ProofKey
import arrow.inject.compiler.plugin.model.ProofAnnotationsFqName
import java.util.concurrent.atomic.AtomicInteger
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.toClassLikeSymbol
import org.jetbrains.kotlin.fir.declarations.FirDeclarationAttributes
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.declarations.FirResolvePhase
import org.jetbrains.kotlin.fir.declarations.builder.buildSimpleFunction
import org.jetbrains.kotlin.fir.declarations.builder.buildTypeParameter
import org.jetbrains.kotlin.fir.declarations.builder.buildValueParameter
import org.jetbrains.kotlin.fir.expressions.buildResolvedArgumentList
import org.jetbrains.kotlin.fir.expressions.builder.buildAnnotation
import org.jetbrains.kotlin.fir.expressions.builder.buildFunctionCall
import org.jetbrains.kotlin.fir.expressions.impl.FirEmptyAnnotationArgumentMapping
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.moduleData
import org.jetbrains.kotlin.fir.originalForSubstitutionOverrideAttr
import org.jetbrains.kotlin.fir.references.builder.buildResolvedNamedReference
import org.jetbrains.kotlin.fir.resolve.constructType
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
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

internal class ResolvedFunctionGenerationExtension(
  session: FirSession,
) : FirDeclarationGenerationExtension(session), FirAbstractProofComponent {

  private val counter: AtomicInteger = AtomicInteger(0)

  private val unitSymbol: FirClassLikeSymbol<*>
    get() = session.builtinTypes.unitType.toClassLikeSymbol(session)!!

  private val compileTimeAnnotationType: ConeLookupTagBasedType
    get() =
      session.symbolProvider.getClassLikeSymbolByClassId(
          ClassId.fromString(
            ProofAnnotationsFqName.CompileTimeAnnotation.asString().replace(".", "/")
          )
        )!!
        .fir.symbol.constructType(emptyArray(), false)

  override fun FirDeclarationPredicateRegistrar.registerPredicates() {
    register(contextPredicate)
    register(injectPredicate)
    register(resolvePredicate)
  }

  override fun generateFunctions(
    callableId: CallableId,
    owner: FirClassSymbol<*>?
  ): List<FirNamedFunctionSymbol> =
    session
      .predicateBasedProvider
      .getSymbolsByPredicate(injectPredicate)
      .filterIsInstance<FirNamedFunctionSymbol>()
      .filter { it.callableId == callableId }
      .map { firNamedFunctionSymbol ->
        val originalSymbol = FirNamedFunctionSymbol(callableId)
        val function = buildSimpleFunction {
          resolvePhase = FirResolvePhase.BODY_RESOLVE
          moduleData = firNamedFunctionSymbol.moduleData
          origin = FirDeclarationOrigin.SubstitutionOverride
          status = firNamedFunctionSymbol.resolvedStatus
          returnTypeRef = firNamedFunctionSymbol.resolvedReturnTypeRef
          name = callableId.callableName
          symbol = originalSymbol
          dispatchReceiverType = firNamedFunctionSymbol.dispatchReceiverType
          annotations += buildAnnotation {
            annotationTypeRef = buildResolvedTypeRef { type = compileTimeAnnotationType }
            argumentMapping = FirEmptyAnnotationArgumentMapping
          }
          typeParameters +=
            firNamedFunctionSymbol.typeParameterSymbols.map { typeParameter ->
              buildTypeParameter {
                moduleData = session.moduleData
                resolvePhase = FirResolvePhase.RAW_FIR
                origin = ProofKey.origin
                attributes = FirDeclarationAttributes() // TODO()
                name = typeParameter.name
                symbol = FirTypeParameterSymbol()
                containingDeclarationSymbol = originalSymbol
                variance = typeParameter.variance
                isReified = typeParameter.isReified
                bounds += typeParameter.resolvedBounds
                annotations += typeParameter.annotations
              }
            }
          valueParameters +=
            firNamedFunctionSymbol.valueParameterSymbols.map { valueParameter ->
              buildValueParameter {
                moduleData = session.moduleData
                resolvePhase = FirResolvePhase.BODY_RESOLVE
                origin = ProofKey.origin
                attributes = valueParameter.fir.attributes
                returnTypeRef = valueParameter.fir.returnTypeRef
                deprecation = valueParameter.fir.deprecation
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
                            variance = Variance.OUT_VARIANCE // TODO()
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
          valueParameters += unambiguousUnitValueParameter()
        }
        function
          .apply { this.originalForSubstitutionOverrideAttr = firNamedFunctionSymbol.fir }
          .symbol
      }

  override fun getCallableNamesForClass(classSymbol: FirClassSymbol<*>): Set<Name> =
    (localProofCollector.collectLocalInjectables() +
        externalProofCollector.collectExternalInjectables())
      .filter { it.classId == classSymbol.classId }
      .map { it.callableName }
      .toSet()

  override fun getTopLevelCallableIds(): Set<CallableId> =
    localProofCollector.collectLocalInjectables() +
      externalProofCollector.collectExternalInjectables()

  override fun hasPackage(packageFqName: FqName): Boolean = true

  private fun generateFreshUnitName() = "_unit${counter.getAndIncrement()}_"

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
}
