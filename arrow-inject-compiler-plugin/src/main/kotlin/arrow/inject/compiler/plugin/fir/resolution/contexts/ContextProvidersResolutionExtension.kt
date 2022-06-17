package arrow.inject.compiler.plugin.fir.resolution.contexts

import arrow.inject.compiler.plugin.fir.FirResolutionProof
import arrow.inject.compiler.plugin.fir.contextReceivers
import arrow.inject.compiler.plugin.fir.resolution.resolver.ProofCache
import arrow.inject.compiler.plugin.model.Proof
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirContextReceiver
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.extensions.FirExpressionResolutionExtension
import org.jetbrains.kotlin.fir.resolve.providers.getSymbolByTypeRef
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.FirTypeProjection
import org.jetbrains.kotlin.fir.types.FirTypeProjectionWithVariance
import org.jetbrains.kotlin.fir.types.FirTypeRef
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.toConeTypeProjection
import org.jetbrains.kotlin.fir.types.type

internal class ContextProvidersResolutionExtension(
  override val proofCache: ProofCache,
  session: FirSession,
) : FirExpressionResolutionExtension(session), FirResolutionProof {

  override val allProofs: List<Proof> by lazy { allCollectedProofs }

  // TODO add resolve proof here instead and iterate over all contexts in the proof
  override fun addNewImplicitReceivers(functionCall: FirFunctionCall): List<ConeKotlinType> =
    (functionCall.contextSyntheticFunctionTypeArguments.mapNotNull {
      it.toConeTypeProjection().type
    } + functionCall.contextsOfSyntheticFunctionProviders(session).map { it.typeRef.coneType })
}

internal fun FirFunctionCall.contextsOfSyntheticFunctionProviders(
  session: FirSession
): List<FirContextReceiver> = buildList {
  val deepRecursiveFunction: DeepRecursiveFunction<FirTypeRef, Unit> =
    DeepRecursiveFunction() { currentType: FirTypeRef ->
      val receivers =
        session.symbolProvider
          .getSymbolByTypeRef<FirBasedSymbol<*>>(currentType)
          ?.fir
          ?.contextReceivers
          .orEmpty()
      addAll(receivers)
      for (receiver in receivers) {
        callRecursive(receiver.typeRef)
      }
    }
  val initialTypeRef =
    (contextsOfSyntheticFunctionTypeArgument as? FirTypeProjectionWithVariance)?.typeRef
  if (initialTypeRef != null) deepRecursiveFunction(initialTypeRef)
}

private val FirFunctionCall.isCallToContextSyntheticFunction: Boolean
  get() = calleeReference.name.asString() == "context"

private val FirFunctionCall.isCallToContextsOfSyntheticFunction: Boolean
  get() = calleeReference.name.asString() == "contextsOf"

private val FirFunctionCall.contextSyntheticFunctionTypeArguments: List<FirTypeProjection>
  get() = if (isCallToContextSyntheticFunction) typeArguments else emptyList()

private val FirFunctionCall.contextsOfSyntheticFunctionTypeArgument: FirTypeProjection?
  get() = if (isCallToContextsOfSyntheticFunction) typeArguments.first() else null
