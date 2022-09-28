package foo.bar

import arrow.inject.annotations.context
import arrow.inject.annotations.Contextual

@Contextual
class Persistence

context(Persistence)
@Contextual class Repo

fun main() {
  context<Persistence>()
  val repo: Repo = Repo()
}
