package foo.bar

import arrow.inject.annotations.Contextual
import arrow.inject.annotations.ContextResolution
import arrow.inject.annotations.context

@Contextual internal fun n(): Int = 42

@Contextual internal fun n2(): Int = 0

context(<!AMBIGUOUS_PROOF_FOR_SUPERTYPE!>Int<!>)
@ContextResolution
fun main() {

}
