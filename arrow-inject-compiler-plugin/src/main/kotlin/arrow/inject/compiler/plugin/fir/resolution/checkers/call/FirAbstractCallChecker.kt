@file:OptIn(SymbolInternals::class)

package arrow.inject.compiler.plugin.fir.resolution.checkers.call

import arrow.inject.compiler.plugin.fir.FirAbstractProofComponent
import arrow.inject.compiler.plugin.fir.FirResolutionProof
import arrow.inject.compiler.plugin.fir.coneType
import arrow.inject.compiler.plugin.model.ProofAnnotationsFqName
import arrow.inject.compiler.plugin.model.ProofAnnotationsFqName.ProviderAnnotation
import arrow.inject.compiler.plugin.model.ProofResolution
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.declarations.FirContextReceiver
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.FirValueParameter
import org.jetbrains.kotlin.fir.expressions.FirCall
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccess
import org.jetbrains.kotlin.fir.expressions.FirResolvable
import org.jetbrains.kotlin.fir.resolve.fqName
import org.jetbrains.kotlin.fir.resolvedSymbol
import org.jetbrains.kotlin.fir.scopes.impl.toConeType
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.types.ConeTypeParameterType
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.render
import org.jetbrains.kotlin.fir.types.toConeTypeProjection
import org.jetbrains.kotlin.fir.types.type
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

internal interface FirAbstractCallChecker : FirAbstractProofComponent, FirResolutionProof {

  fun report(expression: FirCall, context: CheckerContext, reporter: DiagnosticReporter)

  fun proofResolutionList(call: FirCall): Map<ProofResolution?, FirElement> {
    val originalFunction: FirFunction? =
      ((call as? FirResolvable)?.calleeReference?.resolvedSymbol as? FirFunctionSymbol<*>)?.fir

    return if (call is FirQualifiedAccess && originalFunction?.isCompileTimeAnnotated == true) {
      val unresolvedValueParameters: List<FirValueParameter> =
        originalFunction.valueParameters.filter {
          val defaultValue: FirFunctionCall? = (it.defaultValue as? FirFunctionCall)
          defaultValue?.calleeReference?.resolvedSymbol == resolve.symbol
        }
      if (originalFunction.isContextSyntheticFunction()) {
        val targetType = call.typeArguments.firstOrNull()?.toConeTypeProjection()?.type
        if (targetType != null) {
          val contextProofResolution =
            resolveProof(ProviderAnnotation, targetType, null)
          mapOf(contextProofResolution to call)
        } else emptyMap()
      } else
        resolvedValueParametersMap(
          call,
          unresolvedValueParameters,
          originalFunction.contextReceivers
        )
    } else {
      emptyMap()
    }
  }

  private fun FirFunction.isContextSyntheticFunction() =
    symbol.callableId == CallableId(FqName("arrow.inject.annotations"), Name.identifier("context"))

  private fun resolvedValueParametersMap(
    call: FirQualifiedAccess,
    unresolvedValueParameters: List<FirValueParameter>,
    unresolvedContextReceivers: List<FirContextReceiver>
  ): Map<ProofResolution?, FirElement> =
    (unresolvedContextReceivers + unresolvedValueParameters)
      .mapNotNull { valueParameter ->
        valueParameter.contextAnnotation()?.let { valueParameter.proofResolution(call, it) }
      }
      .toMap()

  fun FirElement.proofResolution(
    call: FirQualifiedAccess,
    contextFqName: FqName
  ): Pair<ProofResolution?, FirElement> {
    val type =
      when (val coneType = resolutionTargetType()) {
        is ConeTypeParameterType -> {
          val originalFunction =
            ((call as? FirResolvable)?.calleeReference?.resolvedSymbol as? FirFunctionSymbol<*>)
              ?.fir
          val originalTypeArgumentIndex =
            originalFunction?.typeParameters?.indexOfFirst {
              val originalTypeParameter = it.toConeType()
              originalTypeParameter.render() == coneType.render()
            }
              ?: error("Found unbound parameter to type argument")
          if (originalTypeArgumentIndex != -1) {
            call.typeArguments[originalTypeArgumentIndex].toConeTypeProjection().type
              ?: error("Found call unbound parameter to type argument")
          } else {
            error("Found call unbound parameter to type argument")
          }
        }
        else -> coneType
      }

    val proofResolution = resolveProof(contextFqName, type, null)
    return if (proofResolution.proof == null) {
      null to this
    } else proofResolution to this
  }

  private val FirFunction.isCompileTimeAnnotated: Boolean
    get() =
      annotations.any { firAnnotation ->
        firAnnotation.fqName(session) == ProofAnnotationsFqName.CompileTimeAnnotation
      }

  fun FirElement.resolutionTargetType() =
    when (this) {
      is FirValueParameter -> coneType
      is FirContextReceiver -> typeRef.coneType
      is FirFunctionCall ->
        typeArguments[0].toConeTypeProjection().type!! // contextSyntheticFunction
      else -> error("unsupported $this")
    }

  fun FirElement.contextAnnotation(): FqName? =
    when (this) {
      is FirValueParameter -> metaContextAnnotations.firstOrNull()?.fqName(session)
      is FirContextReceiver -> ProviderAnnotation
      is FirFunctionCall -> ProviderAnnotation // contextSyntheticFunction
      else -> error("unsupported $this")
    }
}
