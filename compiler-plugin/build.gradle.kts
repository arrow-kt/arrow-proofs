import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  alias(libs.plugins.kotlin.jvm)
}

sourceSets {
  test {
    java.srcDirs("src/testGenerated")
  }
}

dependencies {
  implementation(project(":annotations"))
  implementation(libs.classgraph)
  implementation(libs.reflections)
  implementation("org.apache.commons:commons-vfs2:2.9.0")
  compileOnly(libs.kotlin.compiler)
  testImplementation(libs.kotlin.compiler)

  testRuntimeOnly(libs.kotlin.test)
  testRuntimeOnly(libs.kotlin.scriptRuntime)
  testRuntimeOnly(libs.kotlin.annotationsJvm)

  testImplementation(libs.kotlin.compilerInternalTestFramework)
  testImplementation(libs.kotlin.reflect)
  testImplementation(libs.kotlin.stdlib)
  testImplementation("junit:junit:4.13.2")

  testImplementation(platform("org.junit:junit-bom:5.8.0"))
  testImplementation("org.junit.jupiter:junit-jupiter")
  testImplementation("org.junit.platform:junit-platform-commons")
  testImplementation("org.junit.platform:junit-platform-launcher")
  testImplementation("org.junit.platform:junit-platform-runner")
  testImplementation("org.junit.platform:junit-platform-suite-api")
}

tasks.withType<KotlinCompile>().configureEach {
  kotlinOptions {
    useFir = true
    freeCompilerArgs += listOf("-Xcontext-receivers")
  }
}

tasks.create<JavaExec>("generateTests") {
  classpath = sourceSets.test.get().runtimeClasspath
  mainClass.set("arrow.inject.compiler.plugin.GenerateTestsKt")
}

tasks.test {
  testLogging {
    showStandardStreams = true
  }

  useJUnitPlatform()
  doFirst {
    setLibraryProperty("org.jetbrains.kotlin.test.kotlin-stdlib", "kotlin-stdlib")
    setLibraryProperty("org.jetbrains.kotlin.test.kotlin-stdlib-jdk8", "kotlin-stdlib-jdk8")
    setLibraryProperty("org.jetbrains.kotlin.test.kotlin-reflect", "kotlin-reflect")
    setLibraryProperty("org.jetbrains.kotlin.test.kotlin-test", "kotlin-test")
    setLibraryProperty("org.jetbrains.kotlin.test.kotlin-script-runtime", "kotlin-script-runtime")
    setLibraryProperty("org.jetbrains.kotlin.test.kotlin-annotations-jvm", "kotlin-annotations-jvm")
  }

  dependsOn(":annotations:jar")
}

fun Test.setLibraryProperty(propName: String, jarName: String) {
  val path = project.configurations
    .testRuntimeClasspath.get()
    .files
    .find { """$jarName-\d.*jar""".toRegex().matches(it.name) }
    ?.absolutePath
    ?: return
  systemProperty(propName, path)
}
