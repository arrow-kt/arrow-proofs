package foo.bar

import arrow.inject.annotations.Contextual
import arrow.inject.annotations.ContextResolution

@Contextual internal fun n(): Int = 42

@Contextual internal fun d(): Double = 33.0

@ContextResolution
<!AMBIGUOUS_PROOF_FOR_SUPERTYPE!>@ContextResolution<!>
fun main() {
  println(this)
}
