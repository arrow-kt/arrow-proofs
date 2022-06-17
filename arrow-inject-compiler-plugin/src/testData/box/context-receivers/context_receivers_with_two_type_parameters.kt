package foo.bar

import arrow.inject.annotations.Provider
import arrow.inject.annotations.context

@Provider
class A {
  val a = 1
}

@Provider
class B {
  val b = 2
}

fun f(): Int {
  println("123")
  context<A, B>()
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
