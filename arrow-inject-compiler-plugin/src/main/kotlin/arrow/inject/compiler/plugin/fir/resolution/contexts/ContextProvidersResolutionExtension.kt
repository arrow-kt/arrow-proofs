@file:OptIn(SymbolInternals::class)

package arrow.inject.compiler.plugin.fir.resolution.contexts

import arrow.inject.compiler.plugin.fir.FirResolutionProofComponent
import arrow.inject.compiler.plugin.fir.coneType
import arrow.inject.compiler.plugin.fir.resolution.resolver.ProofCache
import arrow.inject.compiler.plugin.model.Proof
import arrow.inject.compiler.plugin.model.ProofAnnotationsFqName
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirContextReceiver
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.extensions.FirExpressionResolutionExtension
import org.jetbrains.kotlin.fir.references.FirErrorNamedReference
import org.jetbrains.kotlin.fir.resolve.diagnostics.ConeInapplicableCandidateError
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
//    val types =
//      functionCall.failedContextReceivers.mapNotNull {
//        val proof =
//          resolveProof(ProofAnnotationsFqName.ProviderAnnotation, it.typeRef.coneType).proof
//        if (proof != null) it.typeRef.coneType else null
//      }

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
        // TODO: check more use cases
        .mapNotNull { basedSymbol -> (basedSymbol?.fir as? FirRegularClass)?.contextReceivers }
        .flatten()
        .filter { contextReceiver ->
          contextReceiver.typeRef.coneType in allProofs.map { proof -> proof.declaration.coneType }
        }
        .toList()

//  private val FirFunctionCall.failedContextReceivers: List<FirContextReceiver>
//    get() =
//      (((calleeReference as? FirErrorNamedReference)?.diagnostic as? ConeInapplicableCandidateError)
//          ?.candidate
//          ?.symbol
//          ?.fir as? FirFunction)
//        ?.contextReceivers.orEmpty()
}
