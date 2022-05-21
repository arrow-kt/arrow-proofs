plugins {
  alias(libs.plugins.kotlin.jvm)
  `maven-publish`
//  alias(libs.plugins.arrowGradleConfig.kotlin)
//  alias(libs.plugins.arrowGradleConfig.publish)
}

//tasks {
//  dokkaGfm { enabled = false }
//  dokkaHtml { enabled = false }
//  dokkaJavadoc { enabled = false }
//  dokkaJekyll { enabled = false }
//}

val sourcesJar by tasks.creating(Jar::class) {
  archiveClassifier.set("sources")
  from(sourceSets.main.get().allSource)
}

publishing {
  publications {
    create<MavenPublication>(name) {
      from(components["java"])
      artifact(sourcesJar)
    }
  }
}
