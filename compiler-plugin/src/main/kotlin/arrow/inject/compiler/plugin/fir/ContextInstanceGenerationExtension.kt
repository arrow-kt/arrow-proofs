package arrow.inject.compiler.plugin.fir

import arrow.inject.compiler.plugin.fir.utils.FirUtils
import arrow.inject.compiler.plugin.fir.utils.Predicate
import java.util.concurrent.atomic.AtomicInteger
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirPluginKey
import org.jetbrains.kotlin.fir.declarations.FirResolvePhase
import org.jetbrains.kotlin.fir.declarations.builder.buildSimpleFunction
import org.jetbrains.kotlin.fir.declarations.builder.buildValueParameter
import org.jetbrains.kotlin.fir.expressions.buildResolvedArgumentList
import org.jetbrains.kotlin.fir.expressions.builder.buildAnnotation
import org.jetbrains.kotlin.fir.expressions.builder.buildFunctionCall
import org.jetbrains.kotlin.fir.expressions.impl.FirEmptyAnnotationArgumentMapping
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.predicate.has
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.moduleData
import org.jetbrains.kotlin.fir.references.builder.buildResolvedNamedReference
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirValueParameterSymbol
import org.jetbrains.kotlin.fir.types.builder.buildResolvedTypeRef
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

class ContextInstanceGenerationExtension(
  session: FirSession,
) : FirDeclarationGenerationExtension(session), FirUtils {

  override fun getTopLevelClassIds(): Set<ClassId> {
    return super.getTopLevelClassIds()
  }

  override fun generateClassLikeDeclaration(classId: ClassId): FirClassLikeSymbol<*>? {
    return super.generateClassLikeDeclaration(classId)
  }

  override fun FirDeclarationPredicateRegistrar.registerPredicates() {
    register(Predicate.CONTEXT_PREDICATE)
    register(Predicate.INJECT_PREDICATE)
    register(Predicate.RESOLVE_PREDICATE)
  }

  @OptIn(SymbolInternals::class)
  override fun generateFunctions(
    callableId: CallableId,
    owner: FirClassSymbol<*>?
  ): List<FirNamedFunctionSymbol> {
    return session
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
                  annotations += valueParameter.fir.annotations
                  defaultValue =
                    if (valueParameter.hasMetaContextAnnotation) {
                      buildFunctionCall {
                        typeRef = session.builtinTypes.nothingType
                        argumentList = buildResolvedArgumentList(LinkedHashMap())
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
  }

  override fun getCallableNamesForClass(classSymbol: FirClassSymbol<*>): Set<Name> {
    return super.getCallableNamesForClass(classSymbol)
  }

  override fun getNestedClassifiersNames(classSymbol: FirClassSymbol<*>): Set<Name> {
    return super.getNestedClassifiersNames(classSymbol)
  }

  override fun getTopLevelCallableIds(): Set<CallableId> =
    session
      .predicateBasedProvider
      .getSymbolsByPredicate(Predicate.INJECT_PREDICATE)
      .mapNotNull { (it as? FirNamedFunctionSymbol)?.callableId }
      .toSet()

  override fun hasPackage(packageFqName: FqName): Boolean {
    return true
  }

  override val counter: AtomicInteger = AtomicInteger(0)
}
