package foo.bar

import arrow.inject.annotations.contextsOf
import arrow.inject.annotations.Provider

@Provider class A {
  val a = 42
}

context(A)
@Provider class B {
  val b = a
}

context(B)
class C {
  val c = b
}

fun f(): Int {
  println("will drop from nested body")
  contextsOf<C>()
  return C().c
}

fun box(): String {
  val result = f()
  return if (result == 42) {
    "OK"
  } else {
    "Fail: $result"
  }
}
