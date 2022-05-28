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
  ":sandbox",
)
