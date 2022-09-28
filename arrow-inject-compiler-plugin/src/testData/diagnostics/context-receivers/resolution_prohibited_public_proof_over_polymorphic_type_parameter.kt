package foo.bar

import arrow.inject.annotations.context
import arrow.inject.annotations.ContextResolution
import arrow.inject.annotations.Contextual

<!OWNERSHIP_VIOLATED_PROOF!>@Contextual fun <A> list(): List<Iterable<A>> = emptyList()<!>

context(List<Iterable<*>>)
@ContextResolution
fun main() {

}
