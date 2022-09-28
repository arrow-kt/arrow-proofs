package foo.bar

import arrow.inject.annotations.ContextResolution
import arrow.inject.annotations.Contextual
import arrow.inject.annotations.context

class Persistence

context(Persistence)
@Contextual class Repo

context(<!UNRESOLVED_CONTEXT_RESOLUTION!>Persistence<!>)
@ContextResolution
fun main() {
  val repo: Repo = Repo()
}
