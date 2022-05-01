package foo.bar

import arrow.inject.annotations.Inject
import foo.bar.annotations.Given

@Given internal val x = "yes!"

@Inject
fun id(@Given evidence: String): String = evidence

fun box(): String {
  val result = id()
  return if (result == "yes!") { "OK" } else { "Fail: $result" }
}
