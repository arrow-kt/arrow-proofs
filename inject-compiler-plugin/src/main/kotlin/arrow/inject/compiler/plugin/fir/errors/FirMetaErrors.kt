package arrow.inject.compiler.plugin.fir.errors

import arrow.inject.compiler.plugin.model.Proof
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.diagnostics.error1
import org.jetbrains.kotlin.diagnostics.error2
import org.jetbrains.kotlin.diagnostics.error3
import org.jetbrains.kotlin.diagnostics.rendering.RootDiagnosticRendererFactory
import org.jetbrains.kotlin.fir.expressions.FirCall
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.types.model.KotlinTypeMarker

internal object FirMetaErrors {

  val PUBLISHED_INTERNAL_ORPHAN by error1<KtDeclaration, Proof>()

  val AMBIGUOUS_PROOF by error2<KtDeclaration, Proof, List<Proof>>()

  val AMBIGUOUS_PROOF_FOR_SUPERTYPE by error3<PsiElement, KotlinTypeMarker, Proof, List<Proof>>()

  val OWNERSHIP_VIOLATED_PROOF by error1<KtDeclaration, Proof>()

  val UNRESOLVED_GIVEN_PROOF by error1<PsiElement, KotlinTypeMarker>()

  val CIRCULAR_CYCLE_ON_GIVEN_PROOF by error2<PsiElement, KotlinTypeMarker, List<Proof>>()

  val UNRESOLVED_GIVEN_CALL_SITE by error2<PsiElement, FirCall, KotlinTypeMarker>()

  init {
    RootDiagnosticRendererFactory.registerFactory(FirMetaErrorsDefaultMessages)
  }
}
