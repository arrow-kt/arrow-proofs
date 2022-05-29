@file:OptIn(SymbolInternals::class)

package arrow.inject.compiler.plugin.fir.resolution.contexts

import arrow.inject.compiler.plugin.fir.FirResolutionProof
import arrow.inject.compiler.plugin.fir.resolution.resolver.ProofCache
import arrow.inject.compiler.plugin.model.Proof
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.extensions.FirExpressionResolutionExtension
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.FirTypeProjection
import org.jetbrains.kotlin.fir.types.toConeTypeProjection
import org.jetbrains.kotlin.fir.types.type

internal class ContextProvidersResolutionExtension(
  override val proofCache: ProofCache,
  session: FirSession,
) : FirExpressionResolutionExtension(session), FirResolutionProof {

  override val allProofs: List<Proof> by lazy { allCollectedProofs }

  override fun addNewImplicitReceivers(functionCall: FirFunctionCall): List<ConeKotlinType> {
    //TODO add resolve proof here instead and iterate over all contexts in the proof
    return functionCall.contextSyntheticFunctionTypeArguments.mapNotNull {
      it.toConeTypeProjection().type
    }
  }

  // TODO: Improve this equality
  private val FirFunctionCall.isCallToContextSyntheticFunction: Boolean
    get() = calleeReference.name.asString() == "context"

  private val FirFunctionCall.contextSyntheticFunctionTypeArguments: List<FirTypeProjection>
    get() = if (isCallToContextSyntheticFunction) typeArguments else emptyList()

  // TODO: inductive is not working (context receivers of context receivers are not being searched)
  //  private val FirFunctionCall.proofContextReceivers: List<FirContextReceiver>
  //    get() =
  //      contextOfTypeArguments
  //        .asSequence()
  //        // TODO: all are `FirTypeProjectionWithVariance`?
  //        .mapNotNull { typeProjection -> (typeProjection as? FirTypeProjectionWithVariance) }
  //        .map { typeProjection ->
  //          session.symbolProvider.getSymbolByTypeRef<FirBasedSymbol<*>>(typeProjection.typeRef)
  //        }
  //        .mapNotNull { basedSymbol ->
  //          basedSymbol?.fir?.contextReceivers(session)
  //        }
  //        .flatten()
  //        .filter { contextReceiver ->
  //          val proofResolution = resolveProof(ProviderAnnotation,
  // contextReceiver.typeRef.coneType)
  //          proofResolution.proof != null
  //        }
  //        .toList()
}
