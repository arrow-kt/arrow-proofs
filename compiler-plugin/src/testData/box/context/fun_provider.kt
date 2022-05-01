package foo.bar

import arrow.inject.annotations.Inject
import foo.bar.annotations.Given

@Inject fun <A> given(@Given a: A): A = a

@Given internal fun x(): String = "yes!"

fun box(): String {
  val result = given<String>()
  return if (result == "yes!") { "OK" } else { "Fail: $result" }
}
