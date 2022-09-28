package arrow.inject.compiler.plugin.fir.resolution.checkers.declaration

import arrow.inject.compiler.plugin.fir.collectors.ExternalProofCollector
import arrow.inject.compiler.plugin.fir.collectors.LocalProofCollectors
import arrow.inject.compiler.plugin.fir.contextReceivers
import arrow.inject.compiler.plugin.fir.errors.FirMetaErrors
import arrow.inject.compiler.plugin.fir.resolution.checkers.resolutionTargetType
import arrow.inject.compiler.plugin.fir.resolution.resolver.ProofCache
import arrow.inject.compiler.plugin.model.Proof
import arrow.inject.compiler.plugin.model.ProofAnnotationsFqName.ContextualAnnotation
import arrow.inject.compiler.plugin.model.ProofResolution
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.diagnostics.AbstractSourceElementPositioningStrategy
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.caches.FirLazyValue
import org.jetbrains.kotlin.fir.caches.firCachesFactory
import org.jetbrains.kotlin.fir.declarations.FirCallableDeclaration
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.ConeTypeParameterType
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.renderReadable

internal class MissingInductiveDeclarationsChecker(
  override val proofCache: ProofCache,
  override val session: FirSession,
) : FirAbstractDeclarationChecker {

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
      val resolvedParameters: Map<ProofResolution?, FirElement> = proofResolutionList(declaration)

      resolvedParameters.forEach { (proofResolution2, element) ->
        element.reportMissingInductiveDependency(declaration, reporter, context)
      }
    }
  }

  private fun FirElement.reportMissingInductiveDependency(
    declaration: FirCallableDeclaration,
    reporter: DiagnosticReporter,
    context: CheckerContext
  ) {
    val returnType = resolutionTargetType()

    val proofResolution: ProofResolution =
      if (returnType is ConeTypeParameterType) {
        val index =
          declaration.contextReceivers
            .map { it.typeRef.coneType }
            .indexOfFirst {
              // TODO comparing `renderReadable` is not a good idea
              it.type.renderReadable() == returnType.renderReadable()
            }
        if (index != -1) {
          val substitutedType: ConeKotlinType = declaration.contextReceivers[index].typeRef.coneType
          resolveProof(ContextualAnnotation, substitutedType, mutableListOf())
        } else {
          error("Unexpected type argument index")
        }
      } else {
        resolveProof(ContextualAnnotation, resolutionTargetType(), mutableListOf())
      }

    if (proofResolution.proof != null) {
      reportContextReceiver(proofResolution, this, reporter, context)
    }
  }

  private fun FirElement.reportContextReceiver(
    proofResolution: ProofResolution,
    element: FirElement,
    reporter: DiagnosticReporter,
    context: CheckerContext
  ) {
    val contextReceivers = proofResolution.proof?.declaration?.contextReceivers

    contextReceivers?.forEach { contextReceiver ->
      val targetType =
        targetTypeRef(
          proofResolution.targetType as ConeKotlinType,
          contextReceiver.typeRef.coneType
        )

      val contextReceiverProof =
        resolveProof(ContextualAnnotation, targetType.type, mutableListOf())

      val expressionSource: KtSourceElement? = element.source

      if (contextReceiverProof.proof == null && expressionSource != null) {
        reporter.report(
          FirMetaErrors.UNRESOLVED_CONTEXT_RESOLUTION.on(
            expressionSource,
            element,
            resolutionTargetType(),
            AbstractSourceElementPositioningStrategy.DEFAULT,
          ),
          context,
        )
      }
    }
  }
}
