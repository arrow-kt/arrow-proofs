package foo.bar

import arrow.inject.annotations.context
import arrow.inject.annotations.ContextResolution
import arrow.inject.annotations.Contextual

<!OWNERSHIP_VIOLATED_PROOF!>@Contextual fun n(): Int = 42<!>

context(Int)
@ContextResolution
fun main() {

}
