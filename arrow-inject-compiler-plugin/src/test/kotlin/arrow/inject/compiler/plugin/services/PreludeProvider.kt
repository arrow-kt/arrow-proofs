package arrow.inject.compiler.plugin.services

import java.io.File
import org.jetbrains.kotlin.test.directives.model.DirectivesContainer
import org.jetbrains.kotlin.test.directives.model.RegisteredDirectives
import org.jetbrains.kotlin.test.directives.model.SimpleDirective
import org.jetbrains.kotlin.test.model.TestFile
import org.jetbrains.kotlin.test.model.TestModule
import org.jetbrains.kotlin.test.services.AdditionalSourceProvider
import org.jetbrains.kotlin.test.services.TestServices

class PreludeProvider(
  testServices: TestServices,
  baseDir: String = ".",
) : AdditionalSourceProvider(testServices) {

  private val preludePath = File("$baseDir/src/testData/prelude/")

  private val directiveToFileMap: Map<SimpleDirective, String> =
    mapOf(
      PreludeAdditionalFilesDirectives.ANNOTATION_DIRECTIVE to
        File("$preludePath/CommonCodeForTest.kt").path,
    )

  override val directiveContainers: List<DirectivesContainer> =
    listOf(PreludeAdditionalFilesDirectives)

  override fun produceAdditionalFiles(
    globalDirectives: RegisteredDirectives,
    module: TestModule
  ): List<TestFile> {
    return buildList {
      for ((directive, path) in directiveToFileMap) {
        if (directive in module.directives) {
          add(File(path).toTestFile())
        }
      }
    }
  }
}
