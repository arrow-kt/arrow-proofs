package foo.bar

import arrow.inject.annotations.Inject
import foo.bar.annotations.Given

@Inject fun <A> given(@Given a: A): A = a

@JvmInline
value class X(val value: String) {

  override fun toString(): String = value
}

@Given fun xProvider(): X = X("yes!")

fun box(): String {
  val result = given<X>()
  return if ("$result" == "yes!") {
    "OK"
  } else {
    "Fail: $result"
  }
}
