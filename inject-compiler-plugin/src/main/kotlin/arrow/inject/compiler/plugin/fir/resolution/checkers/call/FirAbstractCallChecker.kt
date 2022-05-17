@file:OptIn(SymbolInternals::class)

package arrow.inject.compiler.plugin.fir.resolution.checkers.call

import arrow.inject.compiler.plugin.fir.FirAbstractProofComponent
import arrow.inject.compiler.plugin.fir.resolution.resolver.ProofCache
import arrow.inject.compiler.plugin.fir.resolution.resolver.ProofResolutionStageRunner
import arrow.inject.compiler.plugin.model.Proof
import arrow.inject.compiler.plugin.model.ProofAnnotationsFqName
import arrow.inject.compiler.plugin.model.ProofResolution
import arrow.inject.compiler.plugin.model.asProofCacheKey
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.FirValueParameter
import org.jetbrains.kotlin.fir.expressions.FirCall
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccess
import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirResolvable
import org.jetbrains.kotlin.fir.resolve.calls.Candidate
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

internal interface FirAbstractCallChecker : FirAbstractProofComponent {

  val proofCache: ProofCache

  val proofResolutionStageRunner: ProofResolutionStageRunner

  val allProofs: List<Proof>

  fun report(expression: FirCall, context: CheckerContext, reporter: DiagnosticReporter)

  fun Candidate.asProof(): Proof = Proof.Implication(symbol.fir.idSignature, symbol.fir)

  fun resolveProof(contextFqName: FqName, type: ConeKotlinType): ProofResolution =
    proofCandidate(candidates = candidates(contextFqName, type), type = type).apply {
      proofCache.putProofIntoCache(type.asProofCacheKey(contextFqName), this)
    }

  private fun candidates(contextFqName: FqName, type: ConeKotlinType): Set<Candidate> =
    proofResolutionStageRunner.run {
      allProofs.filter { contextFqName in it.declaration.contextFqNames }.matchingCandidates(type)
    }

  private fun proofCandidate(candidates: Set<Candidate>, type: ConeKotlinType): ProofResolution {
    val candidate: Candidate? = candidates.firstOrNull()

    return ProofResolution(
      proof = candidate?.asProof(),
      targetType = type,
      ambiguousProofs =
        candidates.mapNotNull { ambiguousCandidate ->
          ambiguousCandidate?.let { Proof.Implication(it.symbol.fir.idSignature, it.symbol.fir) }
        },
    )
  }

  fun proofResolutionList(call: FirCall): Map<ProofResolution?, FirValueParameter> {
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
  ): Map<ProofResolution?, FirValueParameter> =
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

  private val FirFunction.isCompileTimeAnnotated: Boolean
    get() =
      annotations.any { firAnnotation ->
        firAnnotation.fqName(session) == ProofAnnotationsFqName.CompileTimeAnnotation
      }
}
