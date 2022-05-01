package foo.bar

import arrow.inject.annotations.Inject
import foo.bar.annotations.Given

@Given internal fun n(): Int = 42

@Given
class Foo @Inject constructor (@Given val y: Int) {

  @Inject fun foo(@Given x: Int): Int = x + y
}

fun t(foo: Foo = Foo()): Foo = foo

fun box(): String {
  val result = t().foo()
  return if (result == 42 * 2) { "OK" } else { "Fail: $result" }
}
