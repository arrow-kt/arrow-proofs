plugins {
  alias(libs.plugins.kotlin.jvm)
  `maven-publish`
}

kotlin {
  explicitApi()
}

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
