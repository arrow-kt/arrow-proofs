package foo.bar

import arrow.inject.annotations.context
import arrow.inject.annotations.Provider
import kotlin.PublishedApi

<!PUBLISHED_INTERNAL_ORPHAN!>@Provider @PublishedApi internal fun n(): Int = 0<!>

fun main() {
  context<Int>()
}
