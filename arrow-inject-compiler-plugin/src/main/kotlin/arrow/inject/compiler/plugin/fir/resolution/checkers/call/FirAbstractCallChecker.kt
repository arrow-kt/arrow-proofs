package arrow.inject.compiler.plugin.fir.resolution.checkers.call

import arrow.inject.compiler.plugin.fir.FirAbstractProofComponent
import arrow.inject.compiler.plugin.fir.FirResolutionProof
import arrow.inject.compiler.plugin.fir.coneType
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
import org.jetbrains.kotlin.fir.resolvedSymbol
import org.jetbrains.kotlin.fir.scopes.impl.toConeType
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.types.ConeTypeParameterType
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.renderReadable
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

    return if (call is FirQualifiedAccess && originalFunction != null) {
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
    val targetTypes = typeArguments.map { it.toConeTypeProjection().type }
    return if (targetTypes.isNotEmpty()) {
      targetTypes.fold(mutableMapOf()) { map, type ->
        map.also { if (type != null) it[resolveProof(type, mutableListOf())] = this }
      }
    } else emptyMap()
  }

  fun FirQualifiedAccess.valueParametersResolutionMap(
    originalFunction: FirFunction
  ): Map<ProofResolution?, FirElement> {
    val unresolvedValueParameters: List<FirValueParameter> =
      originalFunction.valueParameters.filter {
        val defaultValue: FirFunctionCall? = (it.defaultValue as? FirFunctionCall)
        defaultValue?.calleeReference?.resolvedSymbol == resolve.symbol
      }
    return resolvedValueParametersMap(this, unresolvedValueParameters)
  }

  private fun FirFunction.isContextSyntheticFunction() =
    symbol.callableId == CallableId(FqName("arrow.inject.annotations"), Name.identifier("context"))

  private fun resolvedValueParametersMap(
    call: FirQualifiedAccess,
    unresolvedValueParameters: List<FirValueParameter>
  ): Map<ProofResolution?, FirElement> {
    val resolvedParams =
      unresolvedValueParameters.map { valueParameter ->
        valueParameter.proofResolution(call)
      }
    return resolvedParams.toMap()
  }

  fun FirElement.proofResolution(call: FirQualifiedAccess): Pair<ProofResolution?, FirElement> {
    val type =
      when (val coneType = resolutionTargetType()) {
        is ConeTypeParameterType -> {
          val originalFunction =
            ((call as? FirResolvable)?.calleeReference?.resolvedSymbol as? FirFunctionSymbol<*>)
              ?.fir
          val originalTypeArgumentIndex =
            originalFunction?.typeParameters?.indexOfFirst {
              val originalTypeParameter = it.toConeType()
              // TODO: comparing `renderReadable` is not a good idea because it may not consider
              // type constraints and other potential unique elements of a type not reflected in the
              // rendered string.
              originalTypeParameter.renderReadable() == coneType.renderReadable()
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

    val proofResolution = resolveProof(type, mutableListOf())
    return if (proofResolution.proof == null) {
      null to this
    } else proofResolution to this
  }

  fun FirElement.resolutionTargetType() =
    when (this) {
      is FirValueParameter -> coneType
      is FirContextReceiver -> typeRef.coneType
      is FirFunctionCall ->
        typeArguments[0].toConeTypeProjection().type!! // contextSyntheticFunction
      else -> error("unsupported $this")
    }
}
