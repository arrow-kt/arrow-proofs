@file:OptIn(InternalDiagnosticFactoryMethod::class, SymbolInternals::class)

package arrow.inject.compiler.plugin.fir.resolution.checkers.call

import arrow.inject.compiler.plugin.fir.FirResolutionProofComponent
import arrow.inject.compiler.plugin.fir.coneType
import arrow.inject.compiler.plugin.fir.errors.FirMetaErrors
import arrow.inject.compiler.plugin.fir.resolution.resolver.ProofCache
import arrow.inject.compiler.plugin.fir.resolution.resolver.ProofResolutionStageRunner
import arrow.inject.compiler.plugin.model.Proof
import arrow.inject.compiler.plugin.model.ProofResolution
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.diagnostics.AbstractSourceElementPositioningStrategy
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.InternalDiagnosticFactoryMethod
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.declarations.FirClass
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.FirValueParameter
import org.jetbrains.kotlin.fir.declarations.primaryConstructorIfAny
import org.jetbrains.kotlin.fir.expressions.FirCall
import org.jetbrains.kotlin.fir.psi
import org.jetbrains.kotlin.fir.resolve.fqName
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.types.ConeTypeParameterType
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.toKtPsiSourceElement

internal class CyclesDetectionChecker(
  override val proofCache: ProofCache,
  override val session: FirSession,
) : FirAbstractCallChecker {

  override val allProofs: List<Proof> by lazy { allCollectedProofs }

  override fun report(expression: FirCall, context: CheckerContext, reporter: DiagnosticReporter) {
    proofResolutionList(expression).let {
      resolvedParameters: Map<ProofResolution?, FirValueParameter> ->

      resolvedParameters.forEach { (proofResolution, valueParameter) ->
        val expressionSource: KtSourceElement? = expression.psi?.toKtPsiSourceElement()

        val cycles = proofResolution?.proof?.cycles.orEmpty()

        val valueParameterConeType = valueParameter.coneType

        if (cycles.size > 1 && expressionSource != null) {
          reporter.report(
            FirMetaErrors.CIRCULAR_CYCLE_ON_GIVEN_PROOF.on(
              expressionSource,
              valueParameterConeType,
              cycles,
              AbstractSourceElementPositioningStrategy.DEFAULT,
            ),
            context,
          )
        }
      }
    }
  }

  private val Proof.cycles: List<Proof>
    get() = mutableListOf<Proof>().apply { cycles(this) }

  private fun Proof.cycles(cycles: MutableList<Proof>) {
    when (val proofDeclaration = declaration) {
      is FirFunction -> {
        proofDeclaration.valueParameters.forEach { valueParameter: FirValueParameter ->
          valueParameter.cycles(cycles)
        }
      }
      is FirClass -> {
        val constructor = proofDeclaration.primaryConstructorIfAny(session)?.fir
        constructor?.valueParameters.orEmpty().forEach { valueParameter: FirValueParameter ->
          valueParameter.cycles(cycles)
        }
      }
      is FirProperty -> {}
      else -> error("Unsupported Proof declaration: $proofDeclaration")
    }
  }

  private fun FirValueParameter.cycles(cycles: MutableList<Proof>) {
    val valueParameter = this
    val contextFqName: FqName? =
      valueParameter.metaContextAnnotations.firstOrNull()?.fqName(session)
    val type = valueParameter.coneType
    if (contextFqName != null && type !is ConeTypeParameterType) {
      val resolvedProof: Proof? = resolveProof(contextFqName, type).proof
      if (resolvedProof != null) {
        val encountered = cycles.count { it == resolvedProof }
        if (encountered <= 1) {
          cycles.add(resolvedProof)
          resolvedProof.cycles(cycles)
        }
      }
    }
  }
}
