package foo.bar

import arrow.inject.annotations.context
import arrow.inject.annotations.Contextual
import arrow.inject.annotations.ContextResolution
import arrow.inject.annotations.contextual

@Contextual
class A

context(A)
class B(val x: Int)

context(A)
@ContextResolution
fun f2(): Int {
  return B(0).x
}

fun f3(): Int = with(A()) {
  return B(0).x
}

fun box(): String {
  val result = f2()
  return if (result == 0) {
    "OK"
  } else {
    "Fail: $result"
  }
}
