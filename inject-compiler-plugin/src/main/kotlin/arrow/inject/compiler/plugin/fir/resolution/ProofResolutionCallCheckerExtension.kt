package arrow.inject.compiler.plugin.fir.resolution

import arrow.inject.compiler.plugin.fir.FirAbstractCallChecker
import arrow.inject.compiler.plugin.fir.resolution.rules.AmbiguousProofsRule
import arrow.inject.compiler.plugin.fir.resolution.rules.CyclesDetectionRule
import arrow.inject.compiler.plugin.fir.resolution.rules.MissingInductiveDependenciesRule
import arrow.inject.compiler.plugin.fir.resolution.rules.OwnershipViolationsRule
import arrow.inject.compiler.plugin.fir.resolution.rules.UnresolvedCallSiteRule
import arrow.inject.compiler.plugin.model.Proof
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

internal class ProofResolutionCallCheckerExtension(
  override val proofCache: ProofCache,
  session: FirSession,
) : FirAdditionalCheckersExtension(session), FirAbstractCallChecker {

  override val allProofs: List<Proof> by lazy { allCollectedProofs }

  override val proofResolutionStageRunner: ProofResolutionStageRunner by lazy {
    ProofResolutionStageRunner(session)
  }

  private val ambiguousProofsRule: AmbiguousProofsRule by lazy {
    AmbiguousProofsRule(proofCache, session)
  }

  private val cyclesDetectionRule: CyclesDetectionRule by lazy {
    CyclesDetectionRule(proofCache, session)
  }

  private val missingInductiveDependenciesRule: MissingInductiveDependenciesRule by lazy {
    MissingInductiveDependenciesRule(proofCache, session)
  }

  private val ownershipViolationsRule: OwnershipViolationsRule by lazy {
    OwnershipViolationsRule(session)
  }

  private val unresolvedCallSiteRule: UnresolvedCallSiteRule by lazy {
    UnresolvedCallSiteRule(proofCache, session)
  }

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
              ownershipViolationsRule.report(declaration, context, reporter)
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
              ambiguousProofsRule.report(expression, context, reporter)
              cyclesDetectionRule.report(expression, context, reporter)
              missingInductiveDependenciesRule.report(expression, context, reporter)
              unresolvedCallSiteRule.report(expression, context, reporter)
            }
          }
        )
    }
}
