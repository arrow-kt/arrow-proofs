package arrow.inject.compiler.plugin.fir

import arrow.inject.compiler.plugin.fir.utils.FirUtils
import arrow.inject.compiler.plugin.fir.utils.Predicate
import arrow.inject.compiler.plugin.fir.utils.hasMetaContextAnnotation
import java.util.concurrent.atomic.AtomicInteger
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirResolvePhase
import org.jetbrains.kotlin.fir.declarations.builder.buildSimpleFunction
import org.jetbrains.kotlin.fir.declarations.builder.buildValueParameter
import org.jetbrains.kotlin.fir.expressions.buildResolvedArgumentList
import org.jetbrains.kotlin.fir.expressions.builder.buildAnnotation
import org.jetbrains.kotlin.fir.expressions.builder.buildFunctionCall
import org.jetbrains.kotlin.fir.expressions.impl.FirEmptyAnnotationArgumentMapping
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.moduleData
import org.jetbrains.kotlin.fir.references.builder.buildResolvedNamedReference
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirValueParameterSymbol
import org.jetbrains.kotlin.fir.types.builder.buildResolvedTypeRef
import org.jetbrains.kotlin.fir.types.builder.buildTypeProjectionWithVariance
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.types.Variance

class ContextInstanceGenerationExtension(
  session: FirSession,
) : FirDeclarationGenerationExtension(session), FirUtils {

  override fun FirDeclarationPredicateRegistrar.registerPredicates() {
    register(Predicate.CONTEXT_PREDICATE)
    register(Predicate.INJECT_PREDICATE)
    register(Predicate.RESOLVE_PREDICATE)
  }

  @OptIn(SymbolInternals::class)
  override fun generateFunctions(
    callableId: CallableId,
    owner: FirClassSymbol<*>?
  ): List<FirNamedFunctionSymbol> =
    session
      .predicateBasedProvider
      .getSymbolsByPredicate(Predicate.INJECT_PREDICATE)
      .filterIsInstance<FirNamedFunctionSymbol>()
      .filter { it.callableId == callableId }
      .map { firNamedFunctionSymbol ->
        buildSimpleFunction {
            resolvePhase = FirResolvePhase.BODY_RESOLVE
            moduleData = firNamedFunctionSymbol.moduleData
            origin = firNamedFunctionSymbol.origin
            status = firNamedFunctionSymbol.resolvedStatus
            returnTypeRef = firNamedFunctionSymbol.resolvedReturnTypeRef
            name = callableId.callableName
            symbol = FirNamedFunctionSymbol(callableId)
            dispatchReceiverType = firNamedFunctionSymbol.dispatchReceiverType
            annotations += buildAnnotation {
              annotationTypeRef = buildResolvedTypeRef { type = compileTimeAnnotationType }
              argumentMapping = FirEmptyAnnotationArgumentMapping
            }
            valueParameters +=
              firNamedFunctionSymbol.valueParameterSymbols.map { valueParameter ->
                buildValueParameter {
                  moduleData = session.moduleData
                  resolvePhase = FirResolvePhase.BODY_RESOLVE
                  origin = Key.origin
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
                    if (session.hasMetaContextAnnotation(valueParameter.fir)) {
                      buildFunctionCall {
                        typeRef = valueParameter.resolvedReturnTypeRef
                        argumentList = buildResolvedArgumentList(LinkedHashMap())
                        typeArguments += buildTypeProjectionWithVariance {
                          typeRef = valueParameter.resolvedReturnTypeRef
                          variance = Variance.OUT_VARIANCE
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
            valueParameters += unambiguousValueParameter("unit")
          }
          .symbol
      }

  override fun getTopLevelCallableIds(): Set<CallableId> =
    session
      .predicateBasedProvider
      .getSymbolsByPredicate(Predicate.INJECT_PREDICATE)
      .mapNotNull { (it as? FirNamedFunctionSymbol)?.callableId }
      .toSet()

  override fun hasPackage(packageFqName: FqName): Boolean = true

  override val counter: AtomicInteger = AtomicInteger(0)
}
