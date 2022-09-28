package foo.bar

import arrow.inject.annotations.context
import arrow.inject.annotations.ContextResolution
import arrow.inject.annotations.Contextual

@Contextual
class Persistence

context(Persistence)
@Contextual class Repo

context(Persistence)
@ContextResolution
fun main() {
  val repo: Repo = Repo()
}
