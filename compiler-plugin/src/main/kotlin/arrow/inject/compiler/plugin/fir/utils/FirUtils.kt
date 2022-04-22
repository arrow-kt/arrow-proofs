@file:OptIn(SymbolInternals::class)

package arrow.inject.compiler.plugin.fir.utils

import arrow.inject.compiler.plugin.fir.ContextInstanceGenerationExtension
import arrow.inject.compiler.plugin.fir.Key
import arrow.inject.compiler.plugin.proof.COMPILE_TIME_ANNOTATION
import arrow.inject.compiler.plugin.proof.CONTEXT_ANNOTATION
import arrow.inject.compiler.plugin.proof.INJECT_ANNOTATION
import arrow.inject.compiler.plugin.proof.RESOLVE_ANNOTATION
import java.util.concurrent.atomic.AtomicInteger
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.toClassLikeSymbol
import org.jetbrains.kotlin.fir.declarations.FirConstructor
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.FirResolvePhase
import org.jetbrains.kotlin.fir.declarations.FirSimpleFunction
import org.jetbrains.kotlin.fir.declarations.builder.buildValueParameter
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.expressions.buildResolvedArgumentList
import org.jetbrains.kotlin.fir.expressions.builder.buildFunctionCall
import org.jetbrains.kotlin.fir.extensions.AnnotationFqn
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.moduleData
import org.jetbrains.kotlin.fir.references.builder.buildResolvedNamedReference
import org.jetbrains.kotlin.fir.resolve.constructType
import org.jetbrains.kotlin.fir.resolve.defaultType
import org.jetbrains.kotlin.fir.resolve.fqName
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirValueParameterSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name

interface FirUtils {

  val session: FirSession

  fun FirClassSymbol<*>.hasContextAnnotation() =
    annotations.any { it.fqName(session) == ContextAnnotation }

  val FirValueParameterSymbol.hasMetaContextAnnotation: Boolean
    get() =
      annotations
        .flatMap { firAnnotation ->
          firAnnotation.typeRef.toClassLikeSymbol(session)?.annotations.orEmpty()
        }
        .any { it.fqName(session) == ContextAnnotation }

  fun generateFreshName(name: String) = "_$name${counter.getAndIncrement()}_"
  fun unambiguousValueParameter(name: String) = buildValueParameter {
    val newName = Name.identifier(generateFreshName(name))
    moduleData = session.moduleData
    resolvePhase = FirResolvePhase.BODY_RESOLVE
    origin = Key.origin
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
        this.name = UnitSymbol.name
        resolvedSymbol = UnitSymbol
      }
    }
  }

  val UnitSymbol: FirClassLikeSymbol<*>
    get() = session.builtinTypes.unitType.toClassLikeSymbol(session)!!

  val FirFunction.isCompileTimeAnnotated: Boolean
    get() =
      annotations.any { firAnnotation -> firAnnotation.fqName(session) == CompileTimeAnnotation }

  val resolve: FirSimpleFunction
    get() =
      session
        .predicateBasedProvider
        .getSymbolsByPredicate(Predicate.RESOLVE_PREDICATE)
        .filterIsInstance<FirNamedFunctionSymbol>()
        .first()
        .fir

  val compileTimeAnnotationType
    get() =
      session.symbolProvider.getClassLikeSymbolByClassId(
          ClassId.fromString(COMPILE_TIME_ANNOTATION.replace(".", "/"))
        )!!
        .fir.symbol.constructType(emptyArray(), false)

  val counter: AtomicInteger

  companion object {
    val CompileTimeAnnotation = AnnotationFqn(COMPILE_TIME_ANNOTATION)
    val ContextAnnotation = AnnotationFqn(CONTEXT_ANNOTATION)
    val InjectAnnotation = AnnotationFqn(INJECT_ANNOTATION)
    val ResolveAnnotation = AnnotationFqn(RESOLVE_ANNOTATION)
  }
}

fun FirAnnotation.isContextAnnotation(session: FirSession): Boolean {
  val annotations: List<FirAnnotation> = typeRef.toClassLikeSymbol(session)?.annotations.orEmpty()
  return annotations.any { it.fqName(session) == FirUtils.ContextAnnotation }
}

val FirDeclaration.coneType: ConeKotlinType?
  get() =
    when (this) {
      is FirConstructor -> returnTypeRef.coneType
      is FirSimpleFunction -> returnTypeRef.coneType
      is FirProperty -> returnTypeRef.coneType
      is FirRegularClass -> symbol.defaultType()
      else -> null
    }
