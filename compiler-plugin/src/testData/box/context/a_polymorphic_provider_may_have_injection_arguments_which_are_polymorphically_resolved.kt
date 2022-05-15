package foo.bar

import arrow.inject.annotations.Inject
import foo.bar.annotations.Given

@Given internal fun intProvider(): Int = 42

class Foo<A>(val n: A)

@Given internal fun <A> fooProvider(@Given x: A): Foo<A> = Foo(x)

@Inject fun <A> given(@Given value: A): A = value

fun box(): String {
  val result = given<Foo<Int>>()
  return if (result == Foo(42)) {
    "OK"
  } else {
    "Fail: $result"
  }
}
