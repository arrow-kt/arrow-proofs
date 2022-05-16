@file:OptIn(InternalDiagnosticFactoryMethod::class)

package arrow.inject.compiler.plugin.fir.resolution.rules

import arrow.inject.compiler.plugin.fir.FirAbstractProofComponent
import arrow.inject.compiler.plugin.fir.errors.FirMetaErrors.OWNERSHIP_VIOLATED_PROOF
import arrow.inject.compiler.plugin.model.Proof
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.diagnostics.AbstractSourceElementPositioningStrategy.Companion.DEFAULT
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.InternalDiagnosticFactoryMethod
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.declarations.FirCallableDeclaration
import org.jetbrains.kotlin.fir.declarations.utils.visibility
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.ConeTypeParameterType
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.type

class OwnershipViolationsRule(override val session: FirSession) : FirAbstractProofComponent {

  private val allProofs: List<Proof> by lazy { allCollectedProofs }

  /**
   * Public Proofs are only valid, if they don't impose inconsistencies in the resolution process.
   * That is the associated type or types in the proof have to be user owned.
   * @see isUserOwned
   */
  fun report(
    firCallableDeclaration: FirCallableDeclaration,
    context: CheckerContext,
    reporter: DiagnosticReporter
  ) {
    firCallableDeclaration.takeProofIfViolatingOwnershipRule()?.let {
      val source: KtSourceElement? = it.declaration.source
      if (source != null) {
        reporter.report(OWNERSHIP_VIOLATED_PROOF.on(source, it, DEFAULT), context)
      }
    }
  }

  private fun FirCallableDeclaration.takeProofIfViolatingOwnershipRule(): Proof? =
    takeIf { it.metaContextAnnotations.isNotEmpty() }
      ?.let {
        allProofs.firstOrNull {
          it.declaration.symbol == this.symbol &&
            this.visibility != Visibilities.Internal &&
            !this.returnTypeRef.coneType.isUserOwned()
        }
      }

  /**
   * A type is user-owned, when at least one position of the type signature is a user type in the
   * sources. e.g.: `org.core.Semigroup<A, F>` materialises into `A -> F -> org.core.Semigroup<A,
   * F>` Thereby the user needs to own either `F`, `A` or `org.core.Semigroup` to publish a proof.
   * `F` or `A` can't be type parameters to be user-owned.
   */
  private fun ConeKotlinType.isUserOwned(): Boolean =
    (hasUserSource() && this !is ConeTypeParameterType) ||
      typeArguments.any { it.type?.isUserOwned() == true }

  private fun ConeKotlinType.hasUserSource(): Boolean {
    val classId = (this as? ConeClassLikeType)?.lookupTag?.classId

    return if (classId != null) {
      session.symbolProvider.getClassLikeSymbolByClassId(classId)?.source != null
    } else {
      false
    }
  }
}
