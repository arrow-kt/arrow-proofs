package arrow.inject.compiler.plugin.fir.errors

import arrow.inject.compiler.plugin.proof.Proof
import org.jetbrains.kotlin.diagnostics.SourceElementPositioningStrategies.SELECTOR_BY_QUALIFIED
import org.jetbrains.kotlin.diagnostics.error0
import org.jetbrains.kotlin.diagnostics.error1
import org.jetbrains.kotlin.diagnostics.error2
import org.jetbrains.kotlin.diagnostics.error3
import org.jetbrains.kotlin.diagnostics.rendering.RootDiagnosticRendererFactory
import org.jetbrains.kotlin.fir.expressions.FirCall
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.types.model.KotlinTypeMarker

object FirMetaErrors {

  val PUBLISHED_INTERNAL_ORPHAN by error0<KtDeclaration>(SELECTOR_BY_QUALIFIED)

  val AMBIGUOUS_PROOF by error2<KtDeclaration, Proof, List<Proof>>()

  val AMBIGUOUS_PROOF_FOR_SUPERTYPE by error3<KtDeclaration, KotlinTypeMarker, Proof, List<Proof>>()

  val OWNERSHIP_VIOLATED_PROOF by error1<KtDeclaration, Proof>()

  val UNRESOLVED_GIVEN_PROOF by error1<KtDeclaration, KotlinTypeMarker>()

  val CYCLE_ON_GIVEN_PROOF by error2<KtDeclaration, KotlinTypeMarker, List<Proof>>()

  val UNRESOLVED_GIVEN_CALL_SITE by error2<KtDeclaration, FirCall, KotlinTypeMarker>()

  init {
    RootDiagnosticRendererFactory.registerFactory(FirMetaErrorsDefaultMessages)
  }
}
