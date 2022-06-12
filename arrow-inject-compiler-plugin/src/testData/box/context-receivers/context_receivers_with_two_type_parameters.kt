package foo.bar

import arrow.inject.annotations.context
import arrow.inject.annotations.Provider
import arrow.inject.annotations.contextual

@Provider class Persistence
@Provider class X

context(Persistence, X)
class Repo(val x: Int)

fun f(): Int {
  println("123")
  context<Persistence, X>()
  return Repo(0).x
}

fun f2(): Int {
  println("will drop from nested body")
  return contextual<Persistence, Int>(Persistence()) { 2 }
}

fun box(): String {
  val result = f()
  return if (result == 0) {
    "OK"
  } else {
    "Fail: $result"
  }
}
