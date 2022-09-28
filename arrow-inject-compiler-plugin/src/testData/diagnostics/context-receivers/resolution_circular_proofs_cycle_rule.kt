package foo.bar

import arrow.inject.annotations.context
import arrow.inject.annotations.ContextResolution
import arrow.inject.annotations.Contextual

context(String)
@Contextual internal fun n(): Int = this@String.toInt()

context(Int)
@Contextual internal fun s(): String = this@Int.toString()

context(<!CIRCULAR_CYCLE_ON_GIVEN_PROOF!>String<!>)
@ContextResolution
fun main() {

}
