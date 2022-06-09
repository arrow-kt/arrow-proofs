package arrow.inject.compiler.plugin

import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor

class ArrowInjectCliProcessor : CommandLineProcessor {
  override val pluginId: String = "arrow.inject.compiler.plugin.inject"
  override val pluginOptions: Collection<AbstractCliOption> = emptyList()
}
