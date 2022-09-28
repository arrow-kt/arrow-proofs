package arrow.inject.compiler.plugin.fir.resolution

import arrow.inject.compiler.plugin.fir.FirAbstractProofComponent
import arrow.inject.compiler.plugin.fir.resolution.checkers.call.FirAbstractCallChecker
import arrow.inject.compiler.plugin.fir.resolution.checkers.declaration.FirAbstractDeclarationChecker
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.DeclarationCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirCallableDeclarationChecker
import org.jetbrains.kotlin.fir.analysis.checkers.expression.ExpressionCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirCallChecker
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension
import org.jetbrains.kotlin.fir.declarations.FirCallableDeclaration
import org.jetbrains.kotlin.fir.expressions.FirCall
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar

internal class ProofResolutionCheckerExtension(
  session: FirSession,
  declarationCheckers: List<FirAbstractDeclarationChecker>,
  callCheckers: List<FirAbstractCallChecker>,
) : FirAdditionalCheckersExtension(session), FirAbstractProofComponent {

  override val declarationCheckers: DeclarationCheckers =
    object : DeclarationCheckers() {
      override val callableDeclarationCheckers: Set<FirCallableDeclarationChecker> =
        setOf(
          object : FirCallableDeclarationChecker() {
            override fun check(
              declaration: FirCallableDeclaration,
              context: CheckerContext,
              reporter: DiagnosticReporter
            ) {
              declarationCheckers.forEach { it.report(declaration, context, reporter) }
            }
          }
        )
    }

  override val expressionCheckers: ExpressionCheckers =
    object : ExpressionCheckers() {
      override val callCheckers: Set<FirCallChecker> =
        setOf(
          object : FirCallChecker() {
            override fun check(
              expression: FirCall,
              context: CheckerContext,
              reporter: DiagnosticReporter
            ) {
              callCheckers.forEach { it.report(expression, context, reporter) }
            }
          }
        )
    }

  override fun FirDeclarationPredicateRegistrar.registerPredicates() {
    register(contextualPredicate)
  }
}
