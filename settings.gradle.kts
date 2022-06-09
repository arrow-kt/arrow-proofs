pluginManagement {
  repositories {
    mavenCentral()
    gradlePluginPortal()
    maven(url = "https://maven.pkg.jetbrains.space/kotlin/p/kotlin/bootstrap")
    mavenLocal()
  }
}

dependencyResolutionManagement {
  repositories {
    mavenCentral()
    maven(url = "https://maven.pkg.jetbrains.space/kotlin/p/kotlin/bootstrap")
    mavenLocal()
  }
}

include(
  ":arrow-inject-annotations",
  ":arrow-inject-compiler-plugin",
  ":arrow-inject-gradle-plugin",
)

val localProperties =
  java.util.Properties().apply {
    val localPropertiesFile = file("local.properties").apply { createNewFile() }
    load(localPropertiesFile.inputStream())
  }

val isSandboxEnabled = localProperties.getProperty("sandbox.enabled", "false").toBoolean()

if (isSandboxEnabled) include(":sandbox")
