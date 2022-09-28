package foo.bar

import arrow.inject.annotations.Contextual
import arrow.inject.annotations.ContextResolution

class X {
  val x = "yes!"
}

context(X)
@Contextual class Y {
  val y = x
}

context(Y)
fun foo(id: Int): String = "$id: ${this@Y.y}"

<!UNRESOLVED_GIVEN_CALL_SITE!>@ContextResolution<!>
fun main() {
  foo(1)
}
