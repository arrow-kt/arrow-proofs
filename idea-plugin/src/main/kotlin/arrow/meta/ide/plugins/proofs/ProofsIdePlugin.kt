package arrow.meta.ide.plugins.proofs

import arrow.meta.Plugin
import arrow.meta.ide.IdeMetaPlugin
import arrow.meta.ide.plugins.proofs.markers.proofLineMarkers
import arrow.meta.ide.plugins.proofs.psi.isExtensionProof
import arrow.meta.ide.plugins.proofs.psi.isNegationProof
import arrow.meta.ide.plugins.proofs.psi.isRefinementProof
import arrow.meta.ide.resources.ArrowIcons
import arrow.meta.invoke
import arrow.meta.phases.CompilerContext
import arrow.meta.plugins.proofs.phases.resolve.diagnostics.suppressProvenTypeMismatch
import com.intellij.lang.annotation.Annotator
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.callUtil.getResolvedCall
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import arrow.meta.plugins.proofs.phases.resolve.validateConstructorCall
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

val IdeMetaPlugin.typeProofsIde: Plugin
  get() = "Type Proofs IDE" {
    meta(
      proofLineMarkers(ArrowIcons.INTERSECTION, KtNamedFunction::isExtensionProof),
      proofLineMarkers(ArrowIcons.NEGATION, KtNamedFunction::isNegationProof),
      proofLineMarkers(ArrowIcons.REFINEMENT, KtNamedFunction::isRefinementProof),
      addDiagnosticSuppressor { suppressProvenTypeMismatch(it) },
      addAnnotator(
        annotator = Annotator { element, holder -> // in some situations there are 2 or more error annotations
          element.safeAs<KtCallElement>()?.let { ktCall ->
            val ctx = ktCall.analyze(bodyResolveMode = BodyResolveMode.FULL)
            val calls = ctx.getSliceContents(BindingContext.RESOLVED_CALL)
            calls.forEach { (call, resolvedCall) ->
              resolvedCall?.let {
                if (call.callElement == element) {
                  val module = resolvedCall.resultingDescriptor.module
                  val compilerContext = CompilerContext(element.project)
                  compilerContext.module = module
                  val validation = compilerContext.validateConstructorCall(it)
                  validation.filterNot { entry -> entry.value }.forEach { (msg, _) ->
                    holder.createErrorAnnotation(element, msg)
                  }
                }
              }
            }

//            holder.createErrorAnnotation(f, "This is a call element")?.let { error ->
//             // error.registerUniversalFix(AddModifierFix(f, KtTokens.SUSPEND_KEYWORD), f.identifyingElement?.textRange, null)
//            }
          }
        }
      )
    )
  }


