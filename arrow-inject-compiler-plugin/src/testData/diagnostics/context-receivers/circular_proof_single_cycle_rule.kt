package foo.bar

import arrow.inject.annotations.Provider
import arrow.inject.annotations.context

context(Int)
@Provider internal fun n(): Int {
  return this@Int
}

fun main() {
  <!CIRCULAR_CYCLE_ON_GIVEN_PROOF!>context<Int>()<!>
}
