package foo.bar

import arrow.inject.annotations.Provider
import arrow.inject.annotations.context

@Provider class X {
  val x = "yes!"
}

context(X)
@Provider class Y {
  val y = x
}

context(Y)
fun foo(id: Int): String = "$id: ${this@Y.y}"

fun main() {
  context<Y>()
  foo(1)
}
