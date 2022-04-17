package arrow.inject.compiler.plugin.fir

import arrow.inject.compiler.plugin.fir.utils.FirUtils
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.DeclarationCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.expression.ExpressionCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirCallChecker
import org.jetbrains.kotlin.fir.analysis.checkers.type.FirTypeChecker
import org.jetbrains.kotlin.fir.analysis.checkers.type.FirTypeRefChecker
import org.jetbrains.kotlin.fir.analysis.checkers.type.TypeCheckers
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension
import org.jetbrains.kotlin.fir.expressions.FirCall
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.types.FirTypeRef

class ProofsAdditionalCheckersExtension(
  session: FirSession,
) : FirAdditionalCheckersExtension(session), FirUtils {

  override val expressionCheckers: ExpressionCheckers = object : ExpressionCheckers() {
    override val callCheckers: Set<FirCallChecker> = setOf(object : FirCallChecker() {
      override fun check(
        expression: FirCall,
        context: CheckerContext,
        reporter: DiagnosticReporter
      ) {
        println("FirCallChecker")
      }
    })

  }

  override val typeCheckers: TypeCheckers = object : TypeCheckers() {
    override val typeRefCheckers: Set<FirTypeRefChecker> = setOf(object : FirTypeRefChecker() {
      override fun check(
        typeRef: FirTypeRef,
        context: CheckerContext,
        reporter: DiagnosticReporter
      ) {
        println("FirTypeRef")
      }
    })
  }

  override fun FirDeclarationPredicateRegistrar.registerPredicates() {
    println("EXTENSION: ProofsAdditionalCheckersExtension")
    renderHasContextAnnotation()
  }
}
