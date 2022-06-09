package foo.bar

import arrow.inject.annotations.context
import arrow.inject.annotations.Provider

<!OWNERSHIP_VIOLATED_PROOF!>@Provider fun <A> list(): List<Iterable<A>> = emptyList()<!>

fun main() {
  context<List<Iterable<*>>>()
}
