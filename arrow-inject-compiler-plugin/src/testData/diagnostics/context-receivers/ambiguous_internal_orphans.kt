package foo.bar

import arrow.inject.annotations.Contextual
import arrow.inject.annotations.ContextResolution

@Contextual internal fun n(): Int = 42

@Contextual internal fun n2(): Int = 0

<!AMBIGUOUS_PROOF_FOR_SUPERTYPE!>@ContextResolution<!>
fun main() {
  println(this)
}
