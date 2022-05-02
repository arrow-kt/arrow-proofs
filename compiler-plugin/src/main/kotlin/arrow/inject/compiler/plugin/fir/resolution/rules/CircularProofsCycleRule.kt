@file:OptIn(InternalDiagnosticFactoryMethod::class)

package arrow.inject.compiler.plugin.fir.resolution.rules

import arrow.inject.compiler.plugin.fir.FirAbstractProofComponent
import arrow.inject.compiler.plugin.fir.errors.FirMetaErrors.CIRCULAR_CYCLE_ON_GIVEN_PROOF
import arrow.inject.compiler.plugin.model.Proof
import org.jetbrains.kotlin.diagnostics.AbstractSourceElementPositioningStrategy.Companion.DEFAULT
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.InternalDiagnosticFactoryMethod
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.declarations.FirCallableDeclaration
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.types.ConeKotlinType

class CircularProofsCycleRule(override val session: FirSession) : FirAbstractProofComponent {

  private val allProofs: List<Proof> by lazy { allCollectedProofs }

  fun report(
    declaration: FirCallableDeclaration,
    context: CheckerContext,
    reporter: DiagnosticReporter
  ) {
    if (!declaration.hasMetaContextAnnotation) return

    val type = declaration.coneType
    val proofForCurrentDeclaration = allProofs.firstOrNull { it.declaration.coneType == type }

    val proofDeclaration: FirDeclaration? = proofForCurrentDeclaration?.declaration
    val source = proofDeclaration?.source

    val boundedTypes: List<ConeKotlinType> = proofDeclaration?.boundedTypes.orEmpty()

    if (type != null && proofDeclaration != null && source != null) {
      if ((allProofs - proofForCurrentDeclaration).any { it.declaration.coneType in boundedTypes }
      ) {
        reporter.report(CIRCULAR_CYCLE_ON_GIVEN_PROOF.on(source, type, allProofs, DEFAULT), context)
      }
    }
  }
}
