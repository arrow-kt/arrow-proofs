package foo.bar

import arrow.inject.annotations.Inject
import foo.bar.annotations.Given

@Given internal val x = "yes!"

fun id(@Given evidence: String): String = evidence

fun box(): String {
  val result = id("nope!")
  return if (result == "nope!") { "OK" } else { "Fail: $result" }
}
