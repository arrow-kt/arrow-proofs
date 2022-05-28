package foo.bar

import arrow.inject.annotations.Inject
import foo.bar.annotations.Config
import foo.bar.annotations.Given

@Given internal val x: String = "yes!"
@Config internal val y: String = "nope!"

@Inject fun foo(@Given x: String, @Config y: String): String = "$x to $y"

fun box(): String {
  val result = foo()
  return if (result == "yes! to nope!") {
    "OK"
  } else {
    "Fail: $result"
  }
}
