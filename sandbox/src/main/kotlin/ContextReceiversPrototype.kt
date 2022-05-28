package foo.bar

import arrow.inject.annotations.Provider


class Persistence

context(Persistence)
class Repo {
  fun hello() = println("hi")
}

fun main() {
  Persistence().run {
    Repo().hello()
  }
}

context(Int)
@Provider internal fun n(): Int {
  return this@Int
}
