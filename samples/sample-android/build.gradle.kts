plugins {
  id(libs.plugins.kotlin.jvm.get().pluginId)
  id("io.arrow-kt.proofs")
}

dependencies {
  implementation(kotlin("stdlib"))
}
