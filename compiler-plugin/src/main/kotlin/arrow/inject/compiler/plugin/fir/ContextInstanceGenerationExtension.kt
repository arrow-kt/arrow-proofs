package arrow.inject.compiler.plugin.fir

import arrow.inject.compiler.plugin.fir.utils.FirUtils
import arrow.inject.compiler.plugin.fir.utils.FirUtils.Companion.ContextAnnotation
import arrow.inject.compiler.plugin.fir.utils.FirUtils.Companion.InjectAnnotation
import arrow.inject.compiler.plugin.fir.utils.FirUtils.Companion.ResolveAnnotation
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirResolvePhase
import org.jetbrains.kotlin.fir.declarations.FirSimpleFunction
import org.jetbrains.kotlin.fir.declarations.builder.buildSimpleFunction
import org.jetbrains.kotlin.fir.declarations.builder.buildValueParameterCopy
import org.jetbrains.kotlin.fir.expressions.builder.buildFunctionCall
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.predicate.has
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.references.builder.buildSimpleNamedReference
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

class ContextInstanceGenerationExtension(
  session: FirSession,
) : FirDeclarationGenerationExtension(session), FirUtils {

  override fun getTopLevelClassIds(): Set<ClassId> {
    renderHasContextAnnotation()
    return super.getTopLevelClassIds()
  }

  override fun generateClassLikeDeclaration(classId: ClassId): FirClassLikeSymbol<*>? {
    renderHasContextAnnotation()
    return super.generateClassLikeDeclaration(classId)
  }

  override fun FirDeclarationPredicateRegistrar.registerPredicates() {
    println("EXTENSION: ContextInstanceGenerationExtension")
    register(CONTEXT_PREDICATE)
    register(INJECT_PREDICATE)
    register(RESOLVE_PREDICATE)
  }

  @OptIn(SymbolInternals::class)
  override fun generateFunctions(
    callableId: CallableId,
    owner: FirClassSymbol<*>?
  ): List<FirNamedFunctionSymbol> {
    val resolve: FirSimpleFunction =
      session
        .predicateBasedProvider
        .getSymbolsByPredicate(RESOLVE_PREDICATE)
        .filterIsInstance<FirNamedFunctionSymbol>()
        .first()
        .fir

    return session
      .predicateBasedProvider
      .getSymbolsByPredicate(INJECT_PREDICATE)
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

//            valueParameters +=
//              firNamedFunctionSymbol.valueParameterSymbols.map { valueParameter ->
//                buildValueParameterCopy(valueParameter.fir) {
//                  defaultValue = buildFunctionCall {
//                    source = valueParameter.source
//                    calleeReference = buildSimpleNamedReference {
//                      source = resolve.source
//                      name = resolve.name
//                    }
//                  }
//                }
//              }
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
      .getSymbolsByPredicate(has(InjectAnnotation))
      .mapNotNull { (it as? FirNamedFunctionSymbol)?.callableId }
      .toSet()

  override fun hasPackage(packageFqName: FqName): Boolean {
    return true
  }

  companion object {
    val CONTEXT_PREDICATE = has(ContextAnnotation)
    val INJECT_PREDICATE = has(InjectAnnotation)
    val RESOLVE_PREDICATE = has(ResolveAnnotation)
  }
}
