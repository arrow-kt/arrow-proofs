package foo.bar

import arrow.inject.annotations.context
import arrow.inject.annotations.Contextual

<!OWNERSHIP_VIOLATED_PROOF!>@Contextual fun n(): Int = 42<!>

fun main() {
  context<Int>()
}
