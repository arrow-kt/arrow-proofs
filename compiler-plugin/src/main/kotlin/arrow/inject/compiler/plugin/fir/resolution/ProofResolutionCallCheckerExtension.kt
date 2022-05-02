@file:OptIn(InternalDiagnosticFactoryMethod::class, SymbolInternals::class)

package arrow.inject.compiler.plugin.fir.resolution

import arrow.inject.compiler.plugin.fir.FirAbstractProofComponent
import arrow.inject.compiler.plugin.fir.errors.FirMetaErrors
import arrow.inject.compiler.plugin.fir.resolution.rules.CircularProofsCycleRule
import arrow.inject.compiler.plugin.fir.resolution.rules.OwnershipViolationsRule
import arrow.inject.compiler.plugin.model.Proof
import arrow.inject.compiler.plugin.model.ProofAnnotationsFqName
import arrow.inject.compiler.plugin.model.ProofResolution
import arrow.inject.compiler.plugin.model.asProofCacheKey
import arrow.inject.compiler.plugin.model.putProofIntoCache
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.diagnostics.AbstractSourceElementPositioningStrategy
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
import org.jetbrains.kotlin.fir.declarations.FirClassLikeDeclaration
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.FirValueParameter
import org.jetbrains.kotlin.fir.declarations.constructors
import org.jetbrains.kotlin.fir.expressions.FirCall
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccess
import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirResolvable
import org.jetbrains.kotlin.fir.psi
import org.jetbrains.kotlin.fir.resolve.calls.Candidate
import org.jetbrains.kotlin.fir.resolve.firClassLike
import org.jetbrains.kotlin.fir.resolve.fqName
import org.jetbrains.kotlin.fir.resolvedSymbol
import org.jetbrains.kotlin.fir.scopes.impl.toConeType
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.ConeTypeParameterType
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.render
import org.jetbrains.kotlin.fir.types.toConeTypeProjection
import org.jetbrains.kotlin.fir.types.type
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.toKtPsiSourceElement

internal class ProofResolutionCallCheckerExtension(
  session: FirSession,
) : FirAdditionalCheckersExtension(session), FirAbstractProofComponent {

  private val allProofs: List<Proof> by lazy { allCollectedProofs }

  private val circularProofsCycleRule: CircularProofsCycleRule by lazy {
    CircularProofsCycleRule(session)
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
              circularProofsCycleRule.report(declaration, context, reporter)
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
    unresolvedGivenCallSite(expression).let {
      resolvedParameters: Map<ProofResolution?, FirValueParameter> ->
      resolvedParameters.forEach { (proofResolution, valueParameter) ->
        val source = expression.source
        val proof = proofResolution?.proof
        if (proofResolution?.isAmbiguous == true && source != null && proof != null) {
          reporter.report(
            FirMetaErrors.AMBIGUOUS_PROOF_FOR_SUPERTYPE.on(
              source,
              proofResolution.targetType,
              proofResolution.proof,
              proofResolution.ambiguousProofs,
              AbstractSourceElementPositioningStrategy.DEFAULT,
            ),
            context
          )
        }

        val expressionSource: KtSourceElement? = expression.psi?.toKtPsiSourceElement()

        if (proofResolution?.proof == null && expressionSource != null) {
          reportMissingInductiveDependencies(expression, valueParameter, context, reporter)
          reporter.report(
            FirMetaErrors.UNRESOLVED_GIVEN_CALL_SITE.on(
              expressionSource,
              expression,
              valueParameter.returnTypeRef.coneType,
              AbstractSourceElementPositioningStrategy.DEFAULT,
            ),
            context,
          )
        }
      }
    }

  private fun unresolvedGivenCallSite(call: FirCall): Map<ProofResolution?, FirValueParameter> {
    val originalFunction: FirFunction? =
      ((call as? FirResolvable)?.calleeReference?.resolvedSymbol as? FirFunctionSymbol<*>)?.fir

    return if (call is FirQualifiedAccess && originalFunction?.isCompileTimeAnnotated == true) {
      val unresolvedValueParameters: List<FirValueParameter> =
        originalFunction.valueParameters.filter {
          val defaultValue: FirFunctionCall? = (it.defaultValue as? FirFunctionCall)
          defaultValue?.calleeReference?.resolvedSymbol == resolve.symbol
        }
      resolvedValueParametersMap(call, unresolvedValueParameters)
    } else {
      emptyMap()
    }
  }

  private fun resolvedValueParametersMap(
    call: FirQualifiedAccess,
    unresolvedValueParameters: List<FirValueParameter>,
  ) =
    unresolvedValueParameters
      .mapNotNull { valueParameter: FirValueParameter ->
        val contextFqName: FqName? =
          valueParameter.annotations.firstOrNull { it.isContextAnnotation }?.fqName(session)

        val defaultValue: FirExpression? = valueParameter.defaultValue

        if (contextFqName != null && defaultValue is FirQualifiedAccessExpression) {
          val type =
            when (valueParameter.returnTypeRef.coneType) {
              is ConeTypeParameterType -> {
                val originalFunction =
                  ((call as? FirResolvable)?.calleeReference?.resolvedSymbol
                      as? FirFunctionSymbol<*>)
                    ?.fir
                val originalTypeArgumentIndex =
                  originalFunction?.typeParameters?.indexOfFirst {
                    val originalTypeParameter = it.toConeType()
                    val valueParameterType = valueParameter.returnTypeRef.coneType
                    originalTypeParameter.render() == valueParameterType.render()
                  }
                    ?: error("Found unbound parameter to type argument")
                if (originalTypeArgumentIndex != -1) {
                  call.typeArguments[originalTypeArgumentIndex].toConeTypeProjection().type
                    ?: error("Found call unbound parameter to type argument")
                } else {
                  error("Found call unbound parameter to type argument")
                }
              }
              else -> valueParameter.returnTypeRef.coneType
            }

          val proofResolution = resolveProof(contextFqName, type)
          if (proofResolution.proof == null) null to valueParameter
          else proofResolution to valueParameter
        } else {
          null
        }
      }
      .toMap()

  private fun resolveProof(contextFqName: FqName, type: ConeKotlinType): ProofResolution =
    proofCandidate(candidates = candidates(contextFqName, type), type = type).apply {
      putProofIntoCache(type.asProofCacheKey(contextFqName), this)
      // TODO: IMPORTANT CHECK TYPE IN PROOF_CACHE_KEY
    }

  private fun candidates(contextFqName: FqName, type: ConeKotlinType): Set<Candidate> =
    proofResolutionStageRunner.run {
      allProofs.filter { contextFqName in it.declaration.contextFqNames }.matchingCandidates(type)
    }

  private fun proofCandidate(candidates: Set<Candidate>, type: ConeKotlinType): ProofResolution {
    val candidate: Candidate? = candidates.firstOrNull()

    return ProofResolution(
      proof = candidate?.let { Proof.Implication(it.symbol.fir.idSignature, it.symbol.fir) },
      targetType = type,
      ambiguousProofs =
        (candidates - candidate).mapNotNull { ambiguousCandidate ->
          ambiguousCandidate?.let { Proof.Implication(it.symbol.fir.idSignature, it.symbol.fir) }
        }
    )
  }

  private val proofResolutionStageRunner: ProofResolutionStageRunner by lazy {
    ProofResolutionStageRunner(session)
  }

  private fun reportMissingInductiveDependencies(
    expression: FirCall,
    valueParameter: FirValueParameter,
    context: CheckerContext,
    reporter: DiagnosticReporter,
  ) {
    if (valueParameter.hasMetaContextAnnotation) {
      val classLikeDeclaration: FirClassLikeDeclaration? =
        valueParameter.returnTypeRef.firClassLike(session)

      if (classLikeDeclaration != null && classLikeDeclaration is FirClass) {
        classLikeDeclaration
          .constructors(session)
          .firstOrNull { it.isPrimary }
          ?.valueParameterSymbols?.forEach { valueParameterSymbol ->
            val contextAnnotationFqName =
              valueParameterSymbol.fir.metaContextAnnotations.firstOrNull()?.fqName(session)

            val defaultValue =
              (valueParameterSymbol.fir.defaultValue as? FirQualifiedAccessExpression)

            val parameterResolveProof =
              if (contextAnnotationFqName != null && defaultValue != null) {
                resolveProof(
                  contextAnnotationFqName,
                  valueParameterSymbol.resolvedReturnType,
                )
              } else {
                null
              }
            val valueParameterSource: KtSourceElement? = valueParameter.source
            if (parameterResolveProof?.proof == null && valueParameterSource != null)
              reporter.report(
                FirMetaErrors.UNRESOLVED_GIVEN_CALL_SITE.on(
                  valueParameterSource,
                  expression,
                  valueParameter.returnTypeRef.coneType,
                  AbstractSourceElementPositioningStrategy.DEFAULT,
                ),
                context,
              )
          }
      }
    }
  }

  private val FirFunction.isCompileTimeAnnotated: Boolean
    get() =
      annotations.any { firAnnotation ->
        firAnnotation.fqName(session) == ProofAnnotationsFqName.CompileTimeAnnotation
      }

  private val FirDeclaration.contextFqNames: Set<FqName>
    get() = annotations.filter { it.isContextAnnotation }.mapNotNull { it.fqName(session) }.toSet()
}
