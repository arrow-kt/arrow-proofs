package arrow.inject.compiler.plugin.fir.resolution.checkers.declaration

import arrow.inject.compiler.plugin.fir.FirAbstractProofComponent
import arrow.inject.compiler.plugin.fir.FirResolutionProof
import arrow.inject.compiler.plugin.model.Proof
import arrow.inject.compiler.plugin.model.ProofAnnotationsFqName
import arrow.inject.compiler.plugin.model.ProofResolution
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.caches.FirLazyValue
import org.jetbrains.kotlin.fir.declarations.FirCallableDeclaration
import org.jetbrains.kotlin.fir.types.coneType

internal interface FirAbstractDeclarationChecker : FirAbstractProofComponent, FirResolutionProof {

  fun report(
    declaration: FirCallableDeclaration,
    context: CheckerContext,
    reporter: DiagnosticReporter
  )

  fun proofResolutionList(declaration: FirCallableDeclaration): Map<ProofResolution?, FirElement> {
    return declaration.contextReceiversResolutionMap()
  }

  fun FirCallableDeclaration.contextReceiversResolutionMap(): Map<ProofResolution?, FirElement> {
    return contextReceivers.fold(mutableMapOf()) { map, receiver ->
      map.also {
        it[
          resolveProof(
            ProofAnnotationsFqName.ContextualAnnotation,
            receiver.typeRef.coneType,
            mutableListOf()
          )] = receiver
      }
    }
  }
}
