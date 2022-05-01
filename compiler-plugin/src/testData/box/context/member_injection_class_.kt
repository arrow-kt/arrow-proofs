package foo.bar

import arrow.inject.annotations.Inject
import foo.bar.annotations.Given

@Given internal fun n(): Int = 42

class Foo {

  @Inject fun foo(@Given x: Int): Int = x
}

fun box(): String {
  val result = Foo().foo()
  return if (result == 42) { "OK" } else { "Fail: $result" }
}
