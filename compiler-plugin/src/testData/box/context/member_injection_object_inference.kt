package foo.bar

import arrow.inject.annotations.Inject
import foo.bar.annotations.Given

@Given internal fun n(): Int = 42

object Foo {

  @Inject fun foo(@Given x: Int): Int = x
}

fun box(): String {
  val y = Foo
  val result = y.foo()
  return if (result == 42) { "OK" } else { "Fail: $result" }
}
