package foo.bar

import arrow.inject.annotations.Inject
import foo.bar.annotations.Given


@Given internal fun n(@Given n: Int): Int = n

@Inject fun <A> given(@Given a: A): A = a

val result = <!CIRCULAR_CYCLE_ON_GIVEN_PROOF!>given<Int>()<!>
