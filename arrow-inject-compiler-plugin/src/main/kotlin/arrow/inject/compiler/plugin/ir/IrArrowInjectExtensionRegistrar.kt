package arrow.inject.compiler.plugin.ir

import arrow.inject.compiler.plugin.fir.resolution.resolver.ProofCache
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment

public class IrArrowInjectExtensionRegistrar(
  private val proofCache: ProofCache,
) : IrGenerationExtension {

  override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
    ProofsIrContextReceiversCodegen(proofCache, moduleFragment, pluginContext)
      .generateContextReceivers()
  }
}
