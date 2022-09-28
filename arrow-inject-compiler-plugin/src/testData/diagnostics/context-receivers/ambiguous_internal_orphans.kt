package foo.bar

import arrow.inject.annotations.Contextual
import arrow.inject.annotations.context

@Contextual internal fun n(): Int = 42

@Contextual internal fun n2(): Int = 0

fun main() {
  <!AMBIGUOUS_PROOF_FOR_SUPERTYPE!>context<Int>()<!>
}
