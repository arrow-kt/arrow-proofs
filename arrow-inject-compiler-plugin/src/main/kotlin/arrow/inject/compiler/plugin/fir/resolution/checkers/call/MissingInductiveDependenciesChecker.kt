@file:OptIn(SymbolInternals::class, InternalDiagnosticFactoryMethod::class)

package arrow.inject.compiler.plugin.fir.resolution.checkers.call

import arrow.inject.compiler.plugin.fir.errors.FirMetaErrors
import arrow.inject.compiler.plugin.fir.resolution.resolver.ProofCache
import arrow.inject.compiler.plugin.fir.resolution.resolver.ProofResolutionStageRunner
import arrow.inject.compiler.plugin.model.Proof
import arrow.inject.compiler.plugin.model.ProofResolution
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.diagnostics.AbstractSourceElementPositioningStrategy
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.InternalDiagnosticFactoryMethod
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.declarations.FirClass
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.FirTypeParameterRefsOwner
import org.jetbrains.kotlin.fir.declarations.FirValueParameter
import org.jetbrains.kotlin.fir.declarations.primaryConstructorIfAny
import org.jetbrains.kotlin.fir.expressions.FirCall
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
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

internal class MissingInductiveDependenciesChecker(
  override val proofCache: ProofCache,
  override val session: FirSession,
) : FirAbstractCallChecker {

  override val allProofs: List<Proof> by lazy { allCollectedProofs }

  override fun report(expression: FirCall, context: CheckerContext, reporter: DiagnosticReporter) {
    proofResolutionList(expression).let {
      resolvedParameters: Map<ProofResolution?, FirElement> ->
      resolvedParameters.forEach { (_, valueParameter) ->
        val metaContextAnnotation: FqName? =
          valueParameter.contextAnnotation()

        if (metaContextAnnotation != null) {
          val expressionOwner: FirNamedReference? =
            (expression as? FirFunctionCall)?.calleeReference
          val valueParameterTypeReturnType = valueParameter.coneType()

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
              resolveProof(metaContextAnnotation, valueParameter.coneType())
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

              val targetType =
                targetTypeRef(
                  proofResolution.targetType as ConeKotlinType,
                  firValueParameter.symbol.resolvedReturnType
                )

              val parameterResolveProof =
                if (contextAnnotationFqName != null) {
                  resolveProof(
                    contextAnnotationFqName,
                    targetType.type,
                  )
                } else {
                  null
                }

              val expressionSource: KtSourceElement? = expression.source

              if (parameterResolveProof?.proof == null && expressionSource != null)
                reporter.report(
                  FirMetaErrors.UNRESOLVED_GIVEN_CALL_SITE.on(
                    expressionSource,
                    expression,
                    valueParameter.coneType(),
                    AbstractSourceElementPositioningStrategy.DEFAULT,
                  ),
                  context,
                )
            }
          }
        }
      }
    }
  }
}
