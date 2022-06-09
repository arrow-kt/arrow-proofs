plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.arrowGradleConfig.kotlin)
}

dependencies {
  runtimeOnly(libs.kotlin.stdlib)
  runtimeOnly(project(":arrow-inject-annotations"))
}

tasks {
  named<Delete>("clean") {
    delete("$rootDir/docs/docs/apidocs")
  }

  compileKotlin {
    kotlinOptions.freeCompilerArgs += listOf("-Xskip-runtime-version-check")
  }
}
