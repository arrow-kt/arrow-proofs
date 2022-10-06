package foo.bar

import arrow.inject.annotations.context
import arrow.inject.annotations.Contextual
import arrow.inject.annotations.ContextResolution
import arrow.inject.annotations.contextual

@Contextual
class A

context(A)
@Contextual class B()

context(B)
class C(val x: Int)

fun f2(): Int = contextual(A()) {
  contextual(B()) {
    C(0).x
  }
}

fun box(): String {
  val result = f2()
  return if (result == 0) {
    "OK"
  } else {
    "Fail: $result"
  }
}
