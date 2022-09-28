package foo.bar

import arrow.inject.annotations.Context
import arrow.inject.annotations.ContextResolution

<!OWNERSHIP_VIOLATED_PROOF!>@Contextual fun <A> list(): List<Iterable<A>> = emptyList()<!>

@ContextResolution
fun main() {
  println(this)
}
