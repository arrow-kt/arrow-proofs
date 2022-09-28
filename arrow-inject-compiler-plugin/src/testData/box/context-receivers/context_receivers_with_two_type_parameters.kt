package foo.bar

import arrow.inject.annotations.Contextual
import arrow.inject.annotations.ContextuResolution

@Contextual
class A {
  val a = 1
}

@Contextual
class B {
  val b = 2
}

@ContextuResolution
fun f(): Int {
  println("123")
  return a + b
}

fun box(): String {
  val result = f()
  return if (result == 3) {
    "OK"
  } else {
    "Fail: $result"
  }
}
