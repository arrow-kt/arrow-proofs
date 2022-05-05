@file:OptIn(InternalDiagnosticFactoryMethod::class, SymbolInternals::class, SymbolInternals::class)

package arrow.inject.compiler.plugin.fir.resolution

import arrow.inject.compiler.plugin.fir.FirAbstractCallChecker
import arrow.inject.compiler.plugin.fir.errors.FirMetaErrors.UNRESOLVED_GIVEN_CALL_SITE
import arrow.inject.compiler.plugin.fir.resolution.rules.AmbiguousProofsRule
import arrow.inject.compiler.plugin.fir.resolution.rules.CyclesDetectionRule
import arrow.inject.compiler.plugin.fir.resolution.rules.OwnershipViolationsRule
import arrow.inject.compiler.plugin.model.Proof
import arrow.inject.compiler.plugin.model.ProofResolution
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.diagnostics.AbstractSourceElementPositioningStrategy.Companion.DEFAULT
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.InternalDiagnosticFactoryMethod
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.DeclarationCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirCallableDeclarationChecker
import org.jetbrains.kotlin.fir.analysis.checkers.expression.ExpressionCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirCallChecker
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension
import org.jetbrains.kotlin.fir.declarations.FirCallableDeclaration
import org.jetbrains.kotlin.fir.declarations.FirClass
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.FirTypeParameterRefsOwner
import org.jetbrains.kotlin.fir.declarations.FirValueParameter
import org.jetbrains.kotlin.fir.declarations.primaryConstructorIfAny
import org.jetbrains.kotlin.fir.expressions.FirCall
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.psi
import org.jetbrains.kotlin.fir.references.FirNamedReference
import org.jetbrains.kotlin.fir.resolve.fqName
import org.jetbrains.kotlin.fir.resolvedSymbol
import org.jetbrains.kotlin.fir.scopes.impl.toConeType
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.ConeTypeParameterType
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.render
import org.jetbrains.kotlin.fir.types.toConeTypeProjection
import org.jetbrains.kotlin.fir.types.type
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.toKtPsiSourceElement

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

  private val ownershipViolationsRule: OwnershipViolationsRule by lazy {
    OwnershipViolationsRule(session)
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
              reportUnresolvedGivenCallSite(expression, context, reporter)
            }
          }
        )
    }

  private fun reportUnresolvedGivenCallSite(
    expression: FirCall,
    context: CheckerContext,
    reporter: DiagnosticReporter
  ): Unit =
    proofResolutionList(expression).let {
      resolvedParameters: Map<ProofResolution?, FirValueParameter> ->
      resolvedParameters.forEach { (proofResolution, valueParameter) ->
        val expressionSource: KtSourceElement? = expression.psi?.toKtPsiSourceElement()

        reportMissingInductiveDependencies(expression, valueParameter, context, reporter)
        if (proofResolution?.proof == null && expressionSource != null) {
          reporter.report(
            UNRESOLVED_GIVEN_CALL_SITE.on(
              expressionSource,
              expression,
              valueParameter.returnTypeRef.coneType,
              DEFAULT,
            ),
            context,
          )
        }
      }
    }

  private fun reportMissingInductiveDependencies(
    expression: FirCall,
    valueParameter: FirValueParameter,
    context: CheckerContext,
    reporter: DiagnosticReporter,
  ) {
    val metaContextAnnotation: FqName? =
      valueParameter.metaContextAnnotations.firstOrNull()?.fqName(session)

    if (metaContextAnnotation != null) {
      val expressionOwner: FirNamedReference? = (expression as? FirFunctionCall)?.calleeReference
      val valueParameterTypeReturnType = valueParameter.returnTypeRef.coneType

      val proofResolution: ProofResolution =
        if (valueParameterTypeReturnType is ConeTypeParameterType) {
          val index =
            (expressionOwner?.resolvedSymbol?.fir as? FirTypeParameterRefsOwner)
              ?.typeParameters
              ?.map { it.toConeType() }
              ?.indexOfFirst {
                it.type.render() ==
                  valueParameterTypeReturnType.render() // TODO this may be incorrect
              }
          if (index != null && index != -1) {
            val substitutedType: ConeKotlinType? =
              expression.typeArguments[index].toConeTypeProjection().type

            if (substitutedType != null) {
              resolveProof(metaContextAnnotation, substitutedType)
            } else error("Unexpected type argument index")
          } else {
            error("Unexpected type argument index")
          }
        } else {
          resolveProof(metaContextAnnotation, valueParameter.returnTypeRef.coneType)
        }

      val proofResolutionProof = proofResolution.proof
      val proofResolutionDeclaration = proofResolution.proof?.declaration

      if (proofResolutionProof != null) {
        val valueParameters =
          when (proofResolutionDeclaration) {
            is FirFunction -> proofResolutionDeclaration.valueParameters
            is FirClass ->
              proofResolutionDeclaration
                .primaryConstructorIfAny(session)
                ?.valueParameterSymbols
                ?.map { it.fir }
                .orEmpty()
            else -> emptyList()
          }

        valueParameters.forEach { firValueParameter ->
          val contextAnnotationFqName =
            firValueParameter.metaContextAnnotations.firstOrNull()?.fqName(session)

          val parameterResolveProof =
            if (contextAnnotationFqName != null) {
              resolveProof(
                contextAnnotationFqName,
                firValueParameter.symbol.resolvedReturnType,
              )
            } else {
              null
            }

          val expressionSource: KtSourceElement? = expression.source

          if (parameterResolveProof?.proof == null && expressionSource != null)
            reporter.report(
              UNRESOLVED_GIVEN_CALL_SITE.on(
                expressionSource,
                expression,
                valueParameter.returnTypeRef.coneType,
                DEFAULT,
              ),
              context,
            )
        }
      }
    }
  }
}
