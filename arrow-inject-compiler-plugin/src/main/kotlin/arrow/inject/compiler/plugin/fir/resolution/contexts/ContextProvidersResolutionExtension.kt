@file:OptIn(SymbolInternals::class)

package arrow.inject.compiler.plugin.fir.resolution.contexts

import arrow.inject.compiler.plugin.fir.FirResolutionProofComponent
import arrow.inject.compiler.plugin.fir.coneType
import arrow.inject.compiler.plugin.fir.contextReceivers
import arrow.inject.compiler.plugin.fir.resolution.resolver.ProofCache
import arrow.inject.compiler.plugin.model.Proof
import arrow.inject.compiler.plugin.model.ProofAnnotationsFqName.ProviderAnnotation
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirContextReceiver
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.extensions.FirExpressionResolutionExtension
import org.jetbrains.kotlin.fir.resolve.providers.getSymbolByTypeRef
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.FirTypeProjection
import org.jetbrains.kotlin.fir.types.FirTypeProjectionWithVariance
import org.jetbrains.kotlin.fir.types.coneType

internal class ContextProvidersResolutionExtension(
  override val proofCache: ProofCache,
  session: FirSession,
) : FirExpressionResolutionExtension(session), FirResolutionProofComponent {

  override val allProofs: List<Proof> by lazy { allCollectedProofs }

  override fun addNewImplicitReceivers(functionCall: FirFunctionCall): List<ConeKotlinType> {
    return functionCall.proofContextReceivers.map { it.typeRef.coneType }
  }

  // TODO: Improve this equality
  private val FirFunctionCall.isContextOf: Boolean
    get() = calleeReference.name.asString() == "contextOf"

  private val FirFunctionCall.contextOfTypeArguments: List<FirTypeProjection>
    get() = if (isContextOf) typeArguments else emptyList()

  // TODO: inductive is not working (context receivers of context receivers are not being searched)
  private val FirFunctionCall.proofContextReceivers: List<FirContextReceiver>
    get() =
      contextOfTypeArguments
        .asSequence()
        // TODO: all are `FirTypeProjectionWithVariance`?
        .mapNotNull { typeProjection -> (typeProjection as? FirTypeProjectionWithVariance) }
        .map { typeProjection ->
          session.symbolProvider.getSymbolByTypeRef<FirBasedSymbol<*>>(typeProjection.typeRef)
        }
        .mapNotNull { basedSymbol -> basedSymbol?.fir?.contextReceivers(session) }
        .flatten()
        .filter { contextReceiver ->
          val proofResolution = resolveProof(ProviderAnnotation, contextReceiver.typeRef.coneType)
          proofResolution.proof != null
        }
        .toList()
}
