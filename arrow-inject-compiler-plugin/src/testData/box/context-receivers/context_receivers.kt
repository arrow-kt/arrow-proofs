package foo.bar

import arrow.inject.annotations.Contextual
import arrow.inject.annotations.ContextuResolution

@Contextual class Persistence

context(Persistence)
class Repo(val x: Int)

@ContextResolution
fun f2(): Int {
  println("will drop from nested body")
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
