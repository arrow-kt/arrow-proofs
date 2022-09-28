package arrow.inject.compiler.plugin.fir.resolution.checkers.declaration

import arrow.inject.compiler.plugin.fir.collectors.ExternalProofCollector
import arrow.inject.compiler.plugin.fir.collectors.LocalProofCollectors
import arrow.inject.compiler.plugin.fir.errors.FirMetaErrors
import arrow.inject.compiler.plugin.fir.resolution.resolver.ProofCache
import arrow.inject.compiler.plugin.model.Proof
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.diagnostics.AbstractSourceElementPositioningStrategy
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.caches.FirLazyValue
import org.jetbrains.kotlin.fir.caches.firCachesFactory
import org.jetbrains.kotlin.fir.declarations.FirCallableDeclaration
import org.jetbrains.kotlin.fir.declarations.FirClass
import org.jetbrains.kotlin.fir.declarations.FirContextReceiver
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.primaryConstructorIfAny
import org.jetbrains.kotlin.fir.psi
import org.jetbrains.kotlin.fir.types.ConeTypeParameterType
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.toKtPsiSourceElement

internal class CyclesDetectionChecker(
  override val proofCache: ProofCache,
  override val session: FirSession,
) : FirAbstractDeclarationChecker {

  override val allProofs: List<Proof> by lazy { allCollectedProofs }

  override val allFirLazyProofs: FirLazyValue<List<Proof>, Unit> =
    session.firCachesFactory.createLazyValue {
      LocalProofCollectors(session).collectLocalProofs() +
        ExternalProofCollector(session).collectExternalProofs()
    }

  override fun report(
    declaration: FirCallableDeclaration,
    context: CheckerContext,
    reporter: DiagnosticReporter
  ) {
    if (declaration.hasContextResolutionAnnotation) {
      val resolvedParameters = proofResolutionList(declaration)
      resolvedParameters.forEach { (proofResolution, valueParameter) ->
        val expressionSource: KtSourceElement? = valueParameter.psi?.toKtPsiSourceElement()

        val cycles = proofResolution?.proof?.cycles.orEmpty()

        val valueParameterConeType = valueParameter.resolutionTargetType()

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
        proofDeclaration.contextReceivers.forEach { contextReceiver: FirContextReceiver ->
          contextReceiver.cycles(cycles)
        }
      }
      is FirClass -> {
        val constructor = proofDeclaration.primaryConstructorIfAny(session)?.fir
        constructor?.contextReceivers.orEmpty().forEach { contextReceiver: FirContextReceiver ->
          contextReceiver.cycles(cycles)
        }
      }
      is FirProperty -> {}
      else -> error("Unsupported Proof declaration: $proofDeclaration")
    }
  }

  private fun FirContextReceiver.cycles(cycles: MutableList<Proof>) {
    val type = typeRef.coneType
    if (type !is ConeTypeParameterType) {
      val resolvedProof: Proof? = resolveProof(type = type, mutableListOf()).proof
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
