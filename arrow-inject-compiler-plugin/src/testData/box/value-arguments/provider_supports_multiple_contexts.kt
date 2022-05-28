package foo.bar

import arrow.inject.annotations.Inject
import foo.bar.annotations.Config
import foo.bar.annotations.Given

@Given @Config internal val z: String = "yes!"

@Inject fun foo(@Given x: String, @Config y: String): String = "$x to $y"

fun box(): String {
  val result = foo()
  return if (result == "yes! to yes!") {
    "OK"
  } else {
    "Fail: $result"
  }
}
