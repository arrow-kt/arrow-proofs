package foo.bar

import arrow.inject.annotations.Contextual
import arrow.inject.annotations.context

class Persistence

context(Persistence)

@Contextual class Repo

fun main() {
  <!UNRESOLVED_GIVEN_CALL_SITE!>context<Persistence>()<!>
  val repo: Repo = Repo()
}
