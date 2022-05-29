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
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.ConeTypeParameterType
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.render
import org.jetbrains.kotlin.fir.types.toConeTypeProjection
import org.jetbrains.kotlin.fir.types.toRegularClassSymbol
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
      if (originalFunction.isContextSyntheticFunction()) {
        call.contextReceiversResolutionMap()

      } else {
        call.valueParametersResolutionMap(originalFunction)
      }
    } else {
      emptyMap()
    }
  }
  fun FirQualifiedAccess.contextReceiversResolutionMap(): Map<ProofResolution?, FirElement> {
    val targetType = typeArguments.firstOrNull()?.toConeTypeProjection()?.type
    return if (targetType != null) {
      val contextProofResolution =
        resolveProof(ProviderAnnotation, targetType, mutableListOf())
      mapOf(contextProofResolution to this)
    } else emptyMap()
  }

  fun FirQualifiedAccess.contextReceiversResolutionMap2(): Map<ProofResolution?, FirElement> {
    val targetType = typeArguments.firstOrNull()?.toConeTypeProjection()?.type
    val unresolvedContextReceivers: List<FirContextReceiver> =
      targetType?.toRegularClassSymbol(session)?.fir?.contextReceivers.orEmpty()
//        .filter {
//        val defaultValue: FirFunctionCall? = (it.defaultValue as? FirFunctionCall)
//        defaultValue?.calleeReference?.resolvedSymbol == resolve.symbol
//      }
    return resolvedContextReceiversMap(
      this,
      unresolvedContextReceivers
    )
  }


  fun FirQualifiedAccess.valueParametersResolutionMap(
    originalFunction: FirFunction
  ): Map<ProofResolution?, FirElement> {
    val unresolvedValueParameters: List<FirValueParameter> =
      originalFunction.valueParameters.filter {
        val defaultValue: FirFunctionCall? = (it.defaultValue as? FirFunctionCall)
        defaultValue?.calleeReference?.resolvedSymbol == resolve.symbol
      }
    return resolvedValueParametersMap(
      this,
      unresolvedValueParameters
    )
  }

  private fun FirFunction.isContextSyntheticFunction() =
    symbol.callableId == CallableId(FqName("arrow.inject.annotations"), Name.identifier("context"))

  private fun resolvedValueParametersMap(
    call: FirQualifiedAccess,
    unresolvedValueParameters: List<FirValueParameter>
  ): Map<ProofResolution?, FirElement> {
    val resolvedParams = unresolvedValueParameters
      .mapNotNull { valueParameter ->
        valueParameter.contextAnnotation()?.let { valueParameter.proofResolution(call, it) }
      }
    return resolvedParams.toMap()
  }

  private fun resolvedContextReceiversMap(
    call: FirQualifiedAccess,
    unresolvedContextReceivers: List<FirContextReceiver>
  ): Map<ProofResolution?, FirElement> {
    val resolvedReceivers = unresolvedContextReceivers
      .map { receiverParameter ->
        receiverParameter.proofResolution(call, ProviderAnnotation)
      }
    return resolvedReceivers.toMap()
  }

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

    val proofResolution = resolveProof(contextFqName, type, mutableListOf())
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
