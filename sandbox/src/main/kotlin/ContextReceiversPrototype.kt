package foo.bar


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
