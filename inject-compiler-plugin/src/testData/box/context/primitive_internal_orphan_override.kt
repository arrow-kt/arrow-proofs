package foo.bar

import arrow.inject.annotations.Inject
import foo.bar.annotations.Given

@Given internal fun n(): Int = 42

@Inject fun <A> given(@Given a: A): A = a

fun box(): String {
  val result = given<Int>()
  return if (result == 42) {
    "OK"
  } else {
    "Fail: $result"
  }
}
