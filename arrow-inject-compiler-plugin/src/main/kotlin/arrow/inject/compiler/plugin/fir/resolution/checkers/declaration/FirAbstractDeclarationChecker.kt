package arrow.inject.compiler.plugin.fir.resolution.checkers.declaration

import arrow.inject.compiler.plugin.fir.FirAbstractProofComponent
import arrow.inject.compiler.plugin.fir.FirResolutionProof
import arrow.inject.compiler.plugin.fir.collectors.ExternalProofCollector
import arrow.inject.compiler.plugin.fir.collectors.LocalProofCollectors
import arrow.inject.compiler.plugin.fir.coneType
import arrow.inject.compiler.plugin.fir.resolution.resolver.ProofResolutionStageRunner
import arrow.inject.compiler.plugin.model.Proof
import arrow.inject.compiler.plugin.model.ProofAnnotationsFqName
import arrow.inject.compiler.plugin.model.ProofResolution
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.FirAnnotationContainer
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.caches.FirLazyValue
import org.jetbrains.kotlin.fir.caches.firCachesFactory
import org.jetbrains.kotlin.fir.declarations.FirCallableDeclaration
import org.jetbrains.kotlin.fir.declarations.FirContextReceiver
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.FirSimpleFunction
import org.jetbrains.kotlin.fir.declarations.FirValueParameter
import org.jetbrains.kotlin.fir.expressions.FirCall
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccess
import org.jetbrains.kotlin.fir.expressions.FirResolvable
import org.jetbrains.kotlin.fir.resolve.fqName
import org.jetbrains.kotlin.fir.resolvedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.toConeTypeProjection
import org.jetbrains.kotlin.fir.types.type

internal interface FirAbstractDeclarationChecker : FirAbstractProofComponent, FirResolutionProof {

  val allFirLazyProofs: FirLazyValue<List<Proof>, Unit>

  override val proofResolutionStageRunner: ProofResolutionStageRunner
    get() = ProofResolutionStageRunner(session, this)

  fun report(
    declaration: FirCallableDeclaration,
    context: CheckerContext,
    reporter: DiagnosticReporter
  )

  fun proofResolutionList(declaration: FirCallableDeclaration): Map<ProofResolution?, FirElement> {
    return declaration.contextReceiversResolutionMap()
  }

  fun FirCallableDeclaration.contextReceiversResolutionMap(): Map<ProofResolution?, FirElement> {
    val targetTypes = contextReceivers.map { it.typeRef.coneType }
    return if (targetTypes.isNotEmpty()) {
      targetTypes.fold(mutableMapOf()) { map, type ->
        map.also { it[resolveProof(type, mutableListOf())] = this }
      }
    } else emptyMap()
  }

  fun FirElement.resolutionTargetType() =
    when (this) {
      is FirValueParameter -> coneType
      is FirContextReceiver -> typeRef.coneType
      is FirFunctionCall ->
        typeArguments[0].toConeTypeProjection().type!! // contextSyntheticFunction
      is FirSimpleFunction -> coneType
      else -> error("unsupported $this")
    }

  val FirAnnotationContainer.hasContextResolutionAnnotation: Boolean
    get() {
      return annotations.any { it.fqName(session) == ProofAnnotationsFqName.ContextResolutionAnnotation }
    }
}
