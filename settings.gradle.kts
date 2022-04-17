pluginManagement {
  repositories {
    mavenCentral()
    gradlePluginPortal()
    maven(url = "https://maven.pkg.jetbrains.space/kotlin/p/kotlin/bootstrap")
  }
}

dependencyResolutionManagement {
  repositories {
    mavenCentral()
    maven(url = "https://maven.pkg.jetbrains.space/kotlin/p/kotlin/bootstrap")
  }
}

include(
  "annotations",
  ":compiler-plugin",
)
