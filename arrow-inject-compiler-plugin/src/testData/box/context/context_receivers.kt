package foo.bar

import arrow.inject.annotations.Provider
import arrow.inject.annotations.contextOf

@Provider class Persistence

context(Persistence)

data class Repo {
  val name: String = "Hello"
}

val expectedRepo: Repo
  get() = with(Persistence()) { Repo() }

fun box(): String {
  contextOf<Repo>()
  val result: Repo = Repo()

  return if (result == expectedRepo) {
    "OK"
  } else {
    "Fail: $result"
  }
}
