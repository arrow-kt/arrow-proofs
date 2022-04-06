plugins {
  id(libs.plugins.kotlin.jvm.get().pluginId)
}

dependencies {
  implementation(kotlin("stdlib"))
  implementation(projects.arrowMetaPrelude)

  kotlinCompilerClasspath(projects.arrowProofsPlugin)

  testImplementation(kotlin("test"))
}

tasks.compileKotlin {
  kotlinOptions {

    freeCompilerArgs = listOf(
      "-Xplugin=$rootDir/proofs/proofs-plugin/build/libs/arrow-proofs-plugin-$version.jar",
      "-P", "plugin:arrow.meta.plugin.compiler.proofs:generatedSrcOutputDir=$buildDir/generated/meta/${sourceSets.main.name}/kotlin",
      "-P", "plugin:arrow.meta.plugin.compiler.proofs:baseDir=${project.rootProject.rootDir.path}"
    )
  }
}

kotlin.sourceSets["main"].kotlin.srcDirs("generated/meta/main/kotlin")
