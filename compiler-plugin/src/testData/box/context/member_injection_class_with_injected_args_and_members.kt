package foo.bar

import arrow.inject.annotations.Inject
import foo.bar.annotations.Given

@Given internal fun n(): Int = 42

@Given
class Foo @Inject constructor(@Given val y: Int) {

  @Inject fun foo(@Given x: Int): Int = x + y
}

fun box(): String {
  val result = Foo().foo()
  return if (result == 42 * 2) { "OK" } else { "Fail: $result" }
}
