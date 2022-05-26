package foo.bar

import arrow.inject.annotations.contextOf
import arrow.inject.annotations.Provider

@Provider
class Persistence

context(Persistence)
class Repo

fun main() {
  contextOf<Repo>()
  val repo: Repo = Repo()
}
