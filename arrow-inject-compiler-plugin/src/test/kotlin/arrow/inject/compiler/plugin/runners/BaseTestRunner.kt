package arrow.inject.compiler.plugin.runners

import arrow.inject.compiler.plugin.services.ExtensionRegistrarConfigurator
import arrow.inject.compiler.plugin.services.PluginAnnotationsConfigurator
import arrow.inject.compiler.plugin.services.PreludeAdditionalFilesDirectives
import arrow.inject.compiler.plugin.services.PreludeProvider
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.test.TargetBackend
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.directives.FirDiagnosticsDirectives
import org.jetbrains.kotlin.test.initIdeaConfiguration
import org.jetbrains.kotlin.test.model.DependencyKind
import org.jetbrains.kotlin.test.runners.AbstractKotlinCompilerTest
import org.jetbrains.kotlin.test.runners.baseFirDiagnosticTestConfiguration
import org.jetbrains.kotlin.test.services.EnvironmentBasedStandardLibrariesPathProvider
import org.jetbrains.kotlin.test.services.KotlinStandardLibrariesPathProvider
import org.junit.jupiter.api.BeforeAll

abstract class BaseTestRunner : AbstractKotlinCompilerTest() {
  companion object {
    @BeforeAll
    @JvmStatic
    fun setUp() {
      initIdeaConfiguration()
    }
  }

  override fun createKotlinStandardLibrariesPathProvider(): KotlinStandardLibrariesPathProvider {
    return EnvironmentBasedStandardLibrariesPathProvider
  }
}

fun TestConfigurationBuilder.commonFirWithPluginFrontendConfiguration() {
  baseFirDiagnosticTestConfiguration()

  defaultDirectives {
    +FirDiagnosticsDirectives.ENABLE_PLUGIN_PHASES
    +FirDiagnosticsDirectives.FIR_DUMP
    +PreludeAdditionalFilesDirectives.ANNOTATION_DIRECTIVE
    +PreludeAdditionalFilesDirectives.IDENTITY_DIRECTIVE
  }

  globalDefaults {
    targetBackend = TargetBackend.JVM_IR
    targetPlatform = JvmPlatforms.defaultJvmPlatform
    dependencyKind = DependencyKind.Binary
  }

  useConfigurators(
    ::PluginAnnotationsConfigurator,
    ::ExtensionRegistrarConfigurator,
  )

  useAdditionalSourceProviders(::PreludeProvider)
}
