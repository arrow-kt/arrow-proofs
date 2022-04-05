package arrow.meta.phases.analysis.diagnostic

import arrow.meta.plugins.proofs.phases.Proof
import arrow.meta.plugins.proofs.phases.asString
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.diagnostics.DiagnosticMarker
import org.jetbrains.kotlin.diagnostics.PositioningStrategy
import org.jetbrains.kotlin.diagnostics.hasSyntaxErrors
import org.jetbrains.kotlin.diagnostics.markElement
import org.jetbrains.kotlin.diagnostics.rendering.DiagnosticParameterRenderer
import org.jetbrains.kotlin.diagnostics.rendering.RenderingContext
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.psiUtil.textRangeWithoutComments
import org.jetbrains.kotlin.renderer.DescriptorRenderer
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.types.KotlinType

@JvmField
val RenderProof: DiagnosticParameterRenderer<Proof> =
  object : DiagnosticParameterRenderer<Proof> {
    override fun render(obj: Proof, renderingContext: RenderingContext): String = obj.asString()
  }

@JvmField
val RenderProofs: DiagnosticParameterRenderer<Collection<Proof>> =
  object : DiagnosticParameterRenderer<Collection<Proof>> {
    override fun render(obj: Collection<Proof>, renderingContext: RenderingContext): String =
      obj.joinToString(separator = ",\n") { it.asString() }
  }

val ProofRenderer: DescriptorRenderer = DescriptorRenderer.FQ_NAMES_IN_TYPES

@JvmField
val RenderTypes: DiagnosticParameterRenderer<KotlinType> =
  object : DiagnosticParameterRenderer<KotlinType> {
    override fun render(obj: KotlinType, renderingContext: RenderingContext): String =
      ProofRenderer.renderType(obj)
  }

/**
 * Each [element] that is a witness of this strategy has an attribute that allows internal orphans
 * to be published publicly. e.g.: [PublishedApi] annotation
 */
@JvmField
val onPublishedInternalOrphan: PositioningStrategy<KtDeclaration> =
  position(
    mark = { listOf((it as? KtDeclaration)?.publishedApiAnnotation()?.textRange ?: it.textRange) }
  )

/**
 * matching on the shortName is valid, as the diagnostic is only applied, if the FqName correlates.
 * Which is checked prior to the Diagnostic being applied.
 */
fun KtDeclaration.publishedApiAnnotation(): KtAnnotationEntry? =
  annotationEntries.firstOrNull { it.shortName == StandardNames.FqNames.publishedApi.shortName() }

fun KtDeclaration.onPublishedApi(ctx: BindingContext): Pair<KtAnnotationEntry, TextRange>? =
  annotationEntries
    .firstOrNull {
      ctx.get(BindingContext.ANNOTATION, it)?.fqName == StandardNames.FqNames.publishedApi
    }
    ?.let { it to it.textRangeWithoutComments }

fun position(
  mark: (PsiElement) -> List<TextRange> = { markElement(it) },
  isValid: (PsiElement) -> Boolean = { !hasSyntaxErrors(it) },
  markDiagnostic: (DiagnosticMarker) -> List<TextRange> = { mark(it.psiElement) }
): PositioningStrategy<PsiElement> =
  object : PositioningStrategy<PsiElement>() {
    override fun mark(element: PsiElement): List<TextRange> = mark(element)

    override fun isValid(element: PsiElement): Boolean = isValid(element)

    override fun markDiagnostic(diagnostic: DiagnosticMarker): List<TextRange> =
      markDiagnostic(diagnostic)
  }
