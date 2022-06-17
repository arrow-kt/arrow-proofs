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

@Provider
class C {
  val c = 3
}

@Provider
class D {
  val d = 5
}

@Provider
class E {
  val e = 8
}

fun f(): Int {
  println("123")
  context<A, B, C, D, E>()
  return a + b + c + d + e
}

fun box(): String {
  val result = f()
  return if (result == 19) {
    "OK"
  } else {
    "Fail: $result"
  }
}
