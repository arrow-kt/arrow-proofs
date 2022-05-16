// IGNORE_BACKEND_FIR: JVM_IR
package foo.bar

import arrow.inject.annotations.Inject
import foo.bar.annotations.Given

@Inject fun <A> given(@Given a: A): A = a

@Given internal val x = "yes!"

fun box(): String {
  val result: String = given()
  return if (result == "yes!") {
    "OK"
  } else {
    "Fail: $result"
  }
}
