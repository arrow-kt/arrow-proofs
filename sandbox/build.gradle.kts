import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  alias(libs.plugins.kotlin.jvm)
  //  id("io.arrow-kt.inject") version "0.1.0"
}

tasks.withType<KotlinCompile>().configureEach {
  kotlinOptions {
    //    useK2 = true
    freeCompilerArgs +=
      listOf(
        "-Xcontext-receivers"
        // "-Xplugin=$rootDir/arrow-inject-compiler-plugin/build/libs/arrow-inject-compiler-plugin-0.1.0.jar"
        )
  }
}

dependencies {
  implementation(project(":arrow-inject-annotations"))
//  implementation("io.arrow-kt:arrow-inject-annotations:0.1.0")
}
