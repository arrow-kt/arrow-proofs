package foo.bar

import arrow.inject.annotations.Inject
import foo.bar.annotations.Given
import kotlin.PublishedApi

<!PUBLISHED_INTERNAL_ORPHAN!>@Given @PublishedApi internal fun n(): Int = 0<!>

@Inject fun <A> given(@Given a: A): A = a

val result = given<Int>()
