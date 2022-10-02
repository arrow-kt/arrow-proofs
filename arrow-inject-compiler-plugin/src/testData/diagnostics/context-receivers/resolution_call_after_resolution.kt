package foo.bar

import arrow.inject.annotations.context
import arrow.inject.annotations.Contextual
import arrow.inject.annotations.ContextResolution
import arrow.inject.annotations.contextual

@Contextual
class Persistence

context(Persistence)
class Repo(val x: Int)

context(Persistence)
@ContextResolution
fun f2(): Int {
  return Repo(0).x
}

fun main() {
  f2()
}
