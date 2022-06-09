package arrow.inject.gradle.plugin

import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

public class ArrowInjectGradlePlugin : KotlinCompilerPluginSupportPlugin {

  private val groupId: String = "io.arrow-kt"

  private val artifactId: String = "arrow-inject-compiler-plugin"

  private val pluginId: String = "inject"

  private val version: String = ArrowInjectVersion

  override fun getPluginArtifact(): SubpluginArtifact {
    return SubpluginArtifact(groupId, artifactId, version)
  }

  override fun applyToCompilation(
    kotlinCompilation: KotlinCompilation<*>
  ): Provider<List<SubpluginOption>> {
    val project = kotlinCompilation.target.project
    return project.provider { emptyList() }
  }

  override fun getCompilerPluginId(): String {
    return "arrow.inject.compiler.plugin.$pluginId"
  }

  override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean {
    return true
  }
}
