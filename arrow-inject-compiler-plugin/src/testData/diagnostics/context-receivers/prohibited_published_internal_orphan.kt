package foo.bar

import arrow.inject.annotations.Context
import arrow.inject.annotations.ContextResolution
import kotlin.PublishedApi

<!PUBLISHED_INTERNAL_ORPHAN!>@Contextual @PublishedApi internal fun n(): Int = 0<!>

@ContextResolution
fun main() {
  println(this)
}
