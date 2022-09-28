package foo.bar

import arrow.inject.annotations.Contextual
import arrow.inject.annotations.ContextResolution

@Contextual
class A {
  val a = 1
}

@Contextual
class B {
  val b = 2
}

@Contextual
class C {
  val c = 3
}

@Contextual
class D {
  val d = 5
}

@Contextual
class E {
  val e = 8
}

@ContextResolution
fun f(): Int {
  println("123")
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
