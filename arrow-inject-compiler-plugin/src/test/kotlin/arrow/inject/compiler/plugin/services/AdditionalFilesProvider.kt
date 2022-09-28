package arrow.inject.compiler.plugin.services

import arrow.inject.compiler.plugin.services.AdditionalFilesDirectives.SOME_FILE_DIRECTIVE
import java.io.File
import org.jetbrains.kotlin.test.directives.model.DirectivesContainer
import org.jetbrains.kotlin.test.directives.model.RegisteredDirectives
import org.jetbrains.kotlin.test.directives.model.SimpleDirective
import org.jetbrains.kotlin.test.model.TestFile
import org.jetbrains.kotlin.test.model.TestModule
import org.jetbrains.kotlin.test.services.AdditionalSourceProvider
import org.jetbrains.kotlin.test.services.TestServices

class AdditionalFilesProvider(
  testServices: TestServices,
  baseDir: String = ".",
) : AdditionalSourceProvider(testServices) {

  private val filesPath = File("$baseDir/src/testData/additional-files/")

  private val directiveToFileMap: Map<SimpleDirective, String> =
    mapOf(
      SOME_FILE_DIRECTIVE to File("$filesPath/SomeFile.kt").path,
    )

  override val directiveContainers: List<DirectivesContainer> = listOf(AdditionalFilesDirectives)

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
