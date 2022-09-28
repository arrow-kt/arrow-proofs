package arrow.inject.compiler.plugin.fir.errors

import arrow.inject.compiler.plugin.fir.errors.FirMetaErrors.AMBIGUOUS_PROOF
import arrow.inject.compiler.plugin.fir.errors.FirMetaErrors.AMBIGUOUS_PROOF_FOR_SUPERTYPE
import arrow.inject.compiler.plugin.fir.errors.FirMetaErrors.CIRCULAR_CYCLE_ON_GIVEN_PROOF
import arrow.inject.compiler.plugin.fir.errors.FirMetaErrors.PUBLISHED_INTERNAL_ORPHAN
import arrow.inject.compiler.plugin.fir.errors.FirMetaErrors.UNRESOLVED_CONTEXT_RESOLUTION
import arrow.inject.compiler.plugin.fir.errors.FirMetaErrors.UNRESOLVED_GIVEN_CALL_SITE
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactoryToRendererMap
import org.jetbrains.kotlin.diagnostics.rendering.BaseDiagnosticRendererFactory
import org.jetbrains.kotlin.diagnostics.rendering.Renderer
import org.jetbrains.kotlin.types.model.KotlinTypeMarker

internal object FirMetaErrorsDefaultMessages : BaseDiagnosticRendererFactory() {

  override val MAP: KtDiagnosticFactoryToRendererMap =
    KtDiagnosticFactoryToRendererMap("FirMeta").also { map ->
      map.put(
        PUBLISHED_INTERNAL_ORPHAN,
        "providers can't be published for typs external to this module, " +
          "Please remove the @PublishedApi annotation.",
        RenderProof
      )
      map.put(
        AMBIGUOUS_PROOF,
        "This {0} has following conflicting provider/s: {1}.\nPlease disambiguate " +
          "resolution, by either declaring only one internal / public provider over the" +
          " desired type/s or remove conflicting providers from the project.",
        RenderProof,
        RenderProofs
      )
      map.put(
        AMBIGUOUS_PROOF_FOR_SUPERTYPE,
        "Found ambiguous providers for type {0}. Providers : {2}",
        RENDER_KOTLIN_TYPE_MARKER,
        RenderProof,
        RenderProofs
      )
      map.put(
        FirMetaErrors.OWNERSHIP_VIOLATED_PROOF,
        "This {0} violates ownership rules. Public providers over 3rd party" +
          " types cause ambiguity in resolution. One way to solve this is to" +
          " declare the provider as an internal.",
        RenderProof
      )
      map.put(
        FirMetaErrors.UNRESOLVED_GIVEN_PROOF,
        "The provider on the type {0} can't be inductively resolved. Please" +
          " verify that all parameters have default value or that other injected values" +
          " have a corresponding provider.",
        RENDER_KOTLIN_TYPE_MARKER
      )
      map.put(
        CIRCULAR_CYCLE_ON_GIVEN_PROOF,
        "The provider on the type {0} has cyclic dependencies: {1}. Please verify" +
          " that providers don't depend on each other for resolution.",
        RENDER_KOTLIN_TYPE_MARKER,
        RenderProofs
      )
      map.put(
        UNRESOLVED_GIVEN_CALL_SITE,
        "There is no provider for this type {1} to resolve this call. Either define" +
          " a corresponding provider or pass the required argument for {1} explicitly at this call-site.",
        null,
        RENDER_KOTLIN_TYPE_MARKER
      )
      map.put(
        UNRESOLVED_CONTEXT_RESOLUTION,
        "There is no provider for this type {1} to resolve this call. Either define" +
          " a corresponding provider or pass the required argument for {1} explicitly at this call-site.",
        null,
        RENDER_KOTLIN_TYPE_MARKER
      )
    }
}

private val RENDER_KOTLIN_TYPE_MARKER = Renderer { t: KotlinTypeMarker -> t.toString() }
