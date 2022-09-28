package foo.bar

import arrow.inject.annotations.Context
import arrow.inject.annotations.ContextResolution

<!OWNERSHIP_VIOLATED_PROOF!>@Contextual fun n(): Int = 42<!>

@ContextResolution
fun main() {
  println(this)
}
