package foo.bar

import arrow.inject.annotations.Provider
import arrow.inject.annotations.contextsOf

@Provider internal fun intProvider(): Int = 42

context(Z)
@Provider class B<Z> {
  val b = this@Z
}

context(B<Int>)
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
