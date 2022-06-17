pluginManagement {
  repositories {
    mavenCentral()
    gradlePluginPortal()
    maven(url = "https://oss.sonatype.org/content/repositories/snapshots")
    maven(url = "https://maven.pkg.jetbrains.space/kotlin/p/kotlin/bootstrap")
  }
}

dependencyResolutionManagement {
  repositories {
    mavenCentral()
    maven(url = "https://oss.sonatype.org/content/repositories/snapshots")
    maven(url = "https://maven.pkg.jetbrains.space/kotlin/p/kotlin/bootstrap")
  }
}

include(
  ":arrow-inject-annotations",
  ":arrow-inject-compiler-plugin",
  ":arrow-inject-gradle-plugin",
)

// Docs
include(":arrow-inject-docs")
project(":arrow-inject-docs").projectDir = File("docs")

val localProperties =
  java.util.Properties().apply {
    val localPropertiesFile = file("local.properties").apply { createNewFile() }
    load(localPropertiesFile.inputStream())
  }

val isSandboxEnabled = localProperties.getProperty("sandbox.enabled", "false").toBoolean()

if (isSandboxEnabled) include(":sandbox")
