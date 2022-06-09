package foo.bar

import arrow.inject.annotations.context
import arrow.inject.annotations.Provider

@Provider
class Persistence

context(Persistence)
@Provider class Repo

fun main() {
  context<Persistence>()
  val repo: Repo = Repo()
}
