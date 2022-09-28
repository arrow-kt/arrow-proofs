package foo.bar

import arrow.inject.annotations.context
import arrow.inject.annotations.Contextual
import kotlin.PublishedApi

<!PUBLISHED_INTERNAL_ORPHAN!>@Contextual @PublishedApi internal fun n(): Int = 0<!>

fun main() {
  context<Int>()
}
