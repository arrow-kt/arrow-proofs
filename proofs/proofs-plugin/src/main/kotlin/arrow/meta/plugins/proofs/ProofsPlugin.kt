package arrow.meta.plugins.proofs

import arrow.meta.CliPlugin
import arrow.meta.Meta
import arrow.meta.invoke
import arrow.meta.phases.CompilerContext
import arrow.meta.plugins.proofs.phases.Proof
import arrow.meta.plugins.proofs.phases.asProof
import arrow.meta.plugins.proofs.phases.config.enableProofCallResolver
import arrow.meta.plugins.proofs.phases.ir.ProofsIrCodegen
import arrow.meta.plugins.proofs.phases.ir.removeCompileTimeDeclarations
import arrow.meta.plugins.proofs.phases.isProof
import arrow.meta.plugins.proofs.phases.quotes.generateGivenPreludeFile
import arrow.meta.plugins.proofs.phases.resolve.proofResolutionRules
import org.jetbrains.kotlin.cfg.getElementParentDeclaration
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe

val Meta.typeProofs: CliPlugin
  get() = "Type Proofs CLI" {
    meta(
      enableIr(),
      enableProofCallResolver(),
      declarationChecker { declaration, descriptor, context ->
        val parentDeclaration = declaration.getElementParentDeclaration()
        if (descriptor.isProof() && parentDeclaration != null) {
          localProofsCache[parentDeclaration] =
            localProofsCache[parentDeclaration].orEmpty() +
              descriptor.asProof().filterNot {
                it.through is ValueParameterDescriptor
              }
        }
      },
      proofResolutionRules(),
      generateGivenPreludeFile(),
      irModuleFragment {
        println(renderLocalProofs())
        it
      },
      irCall {
        ProofsIrCodegen(this) {
          proveNestedCalls(it)
        }
      },
      removeCompileTimeDeclarations(),
      irDumpKotlinLike()
    )
  }

val CompilerContext.localProofsCache: MutableMap<KtDeclaration, List<Proof>>
  get() {
    val localProofsKey = "LOCAL_PROOFS"
    if (get<MutableMap<KtDeclaration, List<Proof>>>(localProofsKey) == null) {
      set<MutableMap<KtDeclaration, List<Proof>>>(localProofsKey, mutableMapOf())
    }
    return get(localProofsKey) ?: mutableMapOf()
  }

fun CompilerContext.renderLocalProofs(): String =
  localProofsCache.entries.joinToString("\n") { (parent, proofs) ->
    "${parent.name} = ${proofs.map(Proof::render)}"
  }

fun Proof.render(): String =
  "${to.constructor.declarationDescriptor?.fqNameSafe} -> ${through.name}"
