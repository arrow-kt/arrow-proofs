// IGNORE_BACKEND_FIR: JVM_IR
package foo.bar

import arrow.inject.annotations.Inject
import foo.bar.annotations.Given

@Inject fun <A> given(@Given a: A): A = a

@Given
object X {
  val value = "yes!"
}

fun box(): String {
  val result = given<X>().value
  return if (result == "yes!") {
    "OK"
  } else {
    "Fail: $result"
  }
}
