package foo.bar

import arrow.inject.annotations.context
import arrow.inject.annotations.Provider

<!OWNERSHIP_VIOLATED_PROOF!>@Provider fun n(): Int = 42<!>

fun main() {
  context<Int>()
}
