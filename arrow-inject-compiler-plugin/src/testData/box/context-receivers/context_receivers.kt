package foo.bar

import arrow.inject.annotations.context
import arrow.inject.annotations.Provider
import arrow.inject.annotations.contextual

@Provider
class Persistence

context(Persistence)
class Repo(val x: Int)

fun f2(): Int {
  println("will drop from nested body")
  context<Persistence>()
  return Repo(0).x
}

fun box(): String {
  val result = f2()
  return if (result == 0) {
    "OK"
  } else {
    "Fail: $result"
  }
}
