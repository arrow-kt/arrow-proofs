package foo.bar

import arrow.inject.annotations.Contextual
import arrow.inject.annotations.ContextResolution

context(Int)
@Contextual internal fun n(): Int {
  return this@Int
}

context(<!CIRCULAR_CYCLE_ON_GIVEN_PROOF!>Int<!>)
@ContextResolution
fun main() {
  println(this@Int)
}
