package foo.bar

import arrow.inject.annotations.Contextual
import arrow.inject.annotations.ContextResolution

context(String)
@Contextual internal fun n(): Int = this@String.toInt()

context(Int)
@Contextual internal fun s(): String = this@Int.toString()

context(<!CIRCULAR_CYCLE_ON_GIVEN_PROOF!>Int<!>)
@ContextResolution
fun main() {
  println(this@Int)
}
