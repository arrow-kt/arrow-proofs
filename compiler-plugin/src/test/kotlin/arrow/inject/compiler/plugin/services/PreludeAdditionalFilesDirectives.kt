package arrow.inject.compiler.plugin.services

import org.jetbrains.kotlin.test.directives.model.SimpleDirectivesContainer

object PreludeAdditionalFilesDirectives : SimpleDirectivesContainer() {

  val ANNOTATION_DIRECTIVE by
    directive(
      description =
        """
            Adds common context annotations
            See file ./src/testData/prelude/Annotations.kt
        """.trimIndent()
    )

  val IDENTITY_DIRECTIVE by
  directive(
    description =
    """
            Adds common identity functions
            See file ./src/testData/prelude/Identity.kt
        """.trimIndent()
  )
}
