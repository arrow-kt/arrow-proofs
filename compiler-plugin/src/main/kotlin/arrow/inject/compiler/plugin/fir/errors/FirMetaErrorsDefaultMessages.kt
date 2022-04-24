package arrow.inject.compiler.plugin.fir.errors

import arrow.inject.compiler.plugin.fir.errors.FirMetaErrors.AMBIGUOUS_PROOF
import arrow.inject.compiler.plugin.fir.errors.FirMetaErrors.AMBIGUOUS_PROOF_FOR_SUPERTYPE
import arrow.inject.compiler.plugin.fir.errors.FirMetaErrors.CYCLE_ON_GIVEN_PROOF
import arrow.inject.compiler.plugin.fir.errors.FirMetaErrors.PUBLISHED_INTERNAL_ORPHAN
import arrow.inject.compiler.plugin.fir.errors.FirMetaErrors.UNRESOLVED_GIVEN_CALL_SITE
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactoryToRendererMap
import org.jetbrains.kotlin.diagnostics.rendering.BaseDiagnosticRendererFactory
import org.jetbrains.kotlin.fir.analysis.diagnostics.FirDiagnosticRenderers.RENDER_TYPE

object FirMetaErrorsDefaultMessages : BaseDiagnosticRendererFactory() {

  override val MAP: KtDiagnosticFactoryToRendererMap =
    KtDiagnosticFactoryToRendererMap("FirMeta").also { map ->
      map.put(
        PUBLISHED_INTERNAL_ORPHAN,
        "Internal overrides of proofs are not permitted to be published, " +
          "as they break coherent proof resolution over the kotlin ecosystem. " +
          "Please remove the @PublishedApi annotation."
      )
      map.put(
        AMBIGUOUS_PROOF,
        "This {0} has following conflicting proof/s: {1}.\nPlease disambiguate " +
          "resolution, by either declaring only one internal orphan / public proof over the" +
          " desired type/s or remove conflicting proofs from the project.",
        RenderProof,
        RenderProofs
      )
      map.put(
        AMBIGUOUS_PROOF_FOR_SUPERTYPE,
        "Found ambiguous proofs for type {0}. Proofs : {2}",
        RENDER_TYPE,
        RenderProof,
        RenderProofs
      )
      map.put(
        FirMetaErrors.OWNERSHIP_VIOLATED_PROOF,
        "This {0} violates ownership rules, because public Proofs over 3rd party" +
          " Types break coherence over the kotlin ecosystem. One way to solve this is to" +
          " declare the Proof as an internal orphan.",
        RenderProof
      )
      map.put(
        FirMetaErrors.UNRESOLVED_GIVEN_PROOF,
        "This GivenProof on the type {0} can't be semi-inductively resolved. Please" +
          " verify that all parameters have default value or that other injected given values" +
          " have a corresponding proof.",
        RENDER_TYPE
      )
      map.put(
        CYCLE_ON_GIVEN_PROOF,
        "This GivenProof on the type {0} has cyclic dependencies: {1}. Please verify" +
          " that proofs don't depend on each other for resolution.",
        RENDER_TYPE,
        RenderProofs
      )
      map.put(
        UNRESOLVED_GIVEN_CALL_SITE,
        "There is no Proof for this type {1} to resolve this call. Either define" +
          " a corresponding GivenProof or provide an evidence explicitly at this call-site.",
        null,
        RENDER_TYPE
      )
    }
}
