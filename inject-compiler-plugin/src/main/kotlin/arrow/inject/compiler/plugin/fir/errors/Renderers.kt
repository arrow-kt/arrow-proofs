package arrow.inject.compiler.plugin.fir.errors

import arrow.inject.compiler.plugin.model.Proof
import org.jetbrains.kotlin.diagnostics.rendering.DiagnosticParameterRenderer
import org.jetbrains.kotlin.diagnostics.rendering.RenderingContext
import org.jetbrains.kotlin.fir.FirRenderer

@JvmField
internal val RenderProof: DiagnosticParameterRenderer<Proof> =
  object : DiagnosticParameterRenderer<Proof> {
    override fun render(obj: Proof, renderingContext: RenderingContext): String = obj.asString()
  }

@JvmField
internal val RenderProofs: DiagnosticParameterRenderer<Collection<Proof>> =
  object : DiagnosticParameterRenderer<Collection<Proof>> {
    override fun render(obj: Collection<Proof>, renderingContext: RenderingContext): String =
      obj.joinToString(separator = ",\n") { it.asString() }
  }

internal val ProofRenderer: FirRenderer = FirRenderer(StringBuilder(), FirRenderer.RenderMode.WithFqNames)
