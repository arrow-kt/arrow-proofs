plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.arrowGradleConfig.kotlin)
  alias(libs.plugins.arrowGradleConfig.publish)
}

tasks {
  dokkaGfm { enabled = false }
  dokkaHtml { enabled = false }
  dokkaJavadoc { enabled = false }
  dokkaJekyll { enabled = false }
}
