package foo.bar

import foo.bar.annotations.Given

<!OWNERSHIP_VIOLATED_PROOF!>@Given
fun <A> list(): List<Iterable<A>> = emptyList()<!>
