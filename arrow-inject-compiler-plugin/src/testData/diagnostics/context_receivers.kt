package foo.bar

import arrow.inject.annotations.contextOf
import arrow.inject.annotations.Provider

@Provider
class Persistence

context(Persistence)
@Provider class Repo

fun main() {
  contextOf<Repo>()
  val repo: Repo = Repo()
}

@Provider
class JsonBar

context(JsonBar)
class JsonFoo

fun main2() {
  contextOf<JsonBar>
  //...
}
