package foo.bar

import arrow.inject.annotations.context
import arrow.inject.annotations.Provider

context(String)
@Provider internal fun n(): Int = this@String.toInt()

context(Int)
@Provider internal fun s(): String = this@Int.toString()

fun main() {
  <!CIRCULAR_CYCLE_ON_GIVEN_PROOF!>context<String>()<!>
}
