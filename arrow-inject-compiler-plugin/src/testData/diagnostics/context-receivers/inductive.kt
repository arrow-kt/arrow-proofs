package foo.bar

import arrow.inject.annotations.Contextual
import arrow.inject.annotations.context

@Contextual class X {
  val x = "yes!"
}

context(X)
@Contextual class Y {
  val y = x
}

context(Y)
fun foo(id: Int): String = "$id: ${this@Y.y}"

fun main() {
  context<Y>()
  foo(1)
}
