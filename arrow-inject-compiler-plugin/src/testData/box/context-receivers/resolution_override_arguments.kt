package foo.bar

import arrow.inject.annotations.context
import arrow.inject.annotations.Contextual
import arrow.inject.annotations.ContextResolution
import arrow.inject.annotations.ContextResolved
import arrow.inject.annotations.contextual

interface A {
  val z: Int
}

@Contextual
class DefaultA : A {
  override val z: Int = 0
}

class OverrideA : A {
  override val z: Int = 1
}

context(A)
@Contextual
class B {
  val y: Int = z
}

context(B)
class C {
  val x: Int = y
}

context(B)
@ContextResolution
fun f2(): Int = C().x

fun box(): String {
  val result: Int = f2(a = OverrideA())
  return if (result == 2) {
    "OK"
  } else {
    "Fail: $result"
  }
}
