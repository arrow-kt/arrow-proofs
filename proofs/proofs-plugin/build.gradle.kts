plugins {
  id(libs.plugins.kotlin.jvm.get().pluginId)
  alias(libs.plugins.arrowGradleConfig.kotlin)
  alias(libs.plugins.arrowGradleConfig.publish)
  alias(libs.plugins.arrowGradleConfig.versioning)
}

kotlin {
  explicitApi = null
}

dependencies {
    compileOnly(libs.kotlin.stdlibJDK8)
    implementation(libs.arrowMeta)

    testImplementation(libs.kotlin.stdlibJDK8)
    testImplementation(libs.junit)
    testImplementation(libs.arrowMetaTest)
    testRuntimeOnly(libs.arrowMeta)
    testRuntimeOnly(projects.arrowMetaPrelude)
    testRuntimeOnly(projects.arrowProofsPlugin)
    testRuntimeOnly(libs.arrowCore)
}
