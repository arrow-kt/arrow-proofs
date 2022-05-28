package foo.bar

import arrow.inject.annotations.Provider
import arrow.inject.annotations.context

class Persistence

context(Persistence)

@Provider class Repo

fun main() {
  <!UNRESOLVED_GIVEN_CALL_SITE!>context<Persistence>()<!>
  val repo: Repo = Repo()
}
