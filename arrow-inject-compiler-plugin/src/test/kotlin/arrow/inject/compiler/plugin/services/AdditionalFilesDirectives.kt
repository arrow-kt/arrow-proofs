package arrow.inject.compiler.plugin.services

import org.jetbrains.kotlin.test.directives.model.SimpleDirectivesContainer

object AdditionalFilesDirectives : SimpleDirectivesContainer() {

  val SOME_FILE_DIRECTIVE by
    directive(
      description =
        """
            Adds common context annotations
            See file ./src/testData/additional-files/SomeFile.kt
        """.trimIndent()
    )
}
