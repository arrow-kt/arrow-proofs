enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
enableFeaturePreview("VERSION_CATALOGS")

pluginManagement {
  repositories {
    gradlePluginPortal()
    mavenCentral()
    mavenLocal()
  }
}

dependencyResolutionManagement {
  versionCatalogs {
    create("libs") {
      from(files("gradle/projects.libs.versions.toml"))
      val kotlinVersion: String? by settings
      kotlinVersion?.let { version("kotlin", it) }
    }
  }
}

rootProject.name = "arrow-proof"

// Proofs
include(":arrow-proofs-plugin")
project(":arrow-proofs-plugin").projectDir = File("proofs/proofs-plugin")

include(":arrow-proofs-gradle-plugin")
project(":arrow-proofs-gradle-plugin").projectDir = File("proofs/proofs-gradle-plugin")

include(":arrow-meta-prelude")
project(":arrow-meta-prelude").projectDir = File("proofs/prelude")

include(":samples:sample-android")
include(":samples:sample-jvm")
