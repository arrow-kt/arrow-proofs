package foo.bar

import arrow.inject.annotations.Contextual
import arrow.inject.annotations.ContextResolution

@Contextual
class Persistence

context(Persistence)
class Repo

context(Persistence)
@ContextResolution
fun foo() {
  val repo: Repo = Repo()
}
