package foo.bar

import arrow.inject.annotations.Inject
import foo.bar.annotations.Given

@Given internal fun intProvider(): Int = 42

data class Foo(val n: Int)

@Given internal fun fooProvider(@Given x: Int): Foo = Foo(x)

@Inject fun <A> given(@Given value: A): A = value

fun box(): String {
  val result = given<Foo>()
  return if (result == Foo(42)) {
    "OK"
  } else {
    "Fail: $result"
  }
}
