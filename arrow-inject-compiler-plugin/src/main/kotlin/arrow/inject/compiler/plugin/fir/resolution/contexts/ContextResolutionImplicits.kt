package arrow.inject.compiler.plugin.fir.resolution.contexts

import arrow.inject.compiler.plugin.fir.FirAbstractProofComponent
import arrow.inject.compiler.plugin.fir.FirResolutionProof
import arrow.inject.compiler.plugin.fir.collectors.ExternalProofCollector
import arrow.inject.compiler.plugin.fir.collectors.LocalProofCollectors
import arrow.inject.compiler.plugin.fir.resolution.resolver.ProofCache
import arrow.inject.compiler.plugin.model.Proof
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.caches.FirLazyValue
import org.jetbrains.kotlin.fir.caches.firCachesFactory
import org.jetbrains.kotlin.fir.declarations.FirCallableDeclaration
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.extensions.FirExpressionResolutionExtension
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.coneType

internal class ContextResolutionImplicits(
  override val proofCache: ProofCache,
  session: FirSession,
) : FirExpressionResolutionExtension(session), FirResolutionProof, FirAbstractProofComponent {

  override val allFirLazyProofs: FirLazyValue<List<Proof>, Unit> =
    session.firCachesFactory.createLazyValue {
      LocalProofCollectors(session).collectLocalProofs() +
        ExternalProofCollector(session).collectExternalProofs()
    }

  override fun addNewImplicitReceivers(functionCall: FirFunctionCall): List<ConeKotlinType> {
    val candidateFir = functionCall.calleeReference.candidateSymbol?.fir as? FirCallableDeclaration
    return if (candidateFir?.hasContextResolutionAnnotation == true) {
      candidateFir.contextReceivers.map { it.typeRef.coneType }
    } else emptyList()
  }
}
