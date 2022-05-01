package foo.bar

import arrow.inject.annotations.Inject
import foo.bar.annotations.Given

@Given internal fun n(): Int = 42

@Inject fun t(@Given x: Number): Number = x

fun box(): String {
  val result = t()
  return if (result == 42) { "OK" } else { "Fail: $result" }
}
