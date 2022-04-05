plugins {
  id(libs.plugins.kotlin.jvm.get().pluginId)
//  id("io.arrow-kt.proofs") version "2.0.0-alpha.6"
}

apply(plugin = "io.arrow-kt.proofs")

dependencies {
  implementation(kotlin("stdlib"))
}
