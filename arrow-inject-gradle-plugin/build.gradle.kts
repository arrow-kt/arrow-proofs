import org.jetbrains.kotlin.gradle.internal.ensureParentDirsCreated

plugins {
  `java-gradle-plugin`
  alias(libs.plugins.kotlin.jvm)
  //  alias(libs.plugins.arrowGradleConfig.kotlin)
  alias(libs.plugins.arrowGradleConfig.publish)
}

dependencies {
  compileOnly(libs.kotlin.gradlePluginApi)

  implementation(gradleKotlinDsl())
  implementation(libs.classgraph)
  implementation(libs.kotlin.gradlePluginApi)
}

kotlin {
  explicitApi()
}

tasks {
  dokkaGfm { enabled = false }
  dokkaHtml { enabled = false }
  dokkaJavadoc { enabled = false }
  dokkaJekyll { enabled = false }
}

gradlePlugin {
  plugins {
    create("arrow-inject") {
      id = "io.arrow-kt.inject"
      displayName = "Arrow Inject Gradle Plugin"
      implementationClass = "arrow.inject.gradle.plugin.ArrowInjectGradlePlugin"
    }
  }
}

pluginBundle {
  website = "https://arrow-kt.io/docs/inject"
  vcsUrl = "https://github.com/arrow-kt/arrow-inject"
  description = "Dependency injection framework built with the power of the Kotlin Compiler"
  tags =
    listOf(
      "kotlin",
      "compiler",
      "arrow",
      "plugin",
      "meta",
      "inject",
      "di",
      "dependency injection",
    )
}

generateArrowInjectVersionFile()

fun generateArrowInjectVersionFile() {
  val generatedDir =
    File("$buildDir/generated/main/kotlin/").apply {
      mkdirs()
      File("$this/arrow/inject/gradle/plugin/ArrowInjectVersion.kt").apply {
        ensureParentDirsCreated()
        createNewFile()
        writeText(
          """
            |package arrow.inject.gradle.plugin
            |
            |internal val ArrowInjectVersion = "${project.version}"
            |
          """.trimMargin()
        )
      }
    }

  kotlin.sourceSets.map { it.kotlin.srcDirs(generatedDir) }
}
