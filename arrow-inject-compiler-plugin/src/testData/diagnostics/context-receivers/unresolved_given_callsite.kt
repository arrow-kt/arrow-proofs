package foo.bar

import arrow.inject.annotations.Contextual
import arrow.inject.annotations.ContextResolution

class Persistence

context(Persistence)

@Contextual class Repo

<!UNRESOLVED_GIVEN_CALL_SITE!>@ContextResolution<!>
fun main() {
  val repo: Repo = Repo()
}
