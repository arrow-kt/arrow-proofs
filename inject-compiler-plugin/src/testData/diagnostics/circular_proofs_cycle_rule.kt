package foo.bar

import arrow.inject.annotations.Inject
import foo.bar.annotations.Given

@Given internal fun n(@Given s: String): Int = s.toInt()

@Given internal fun s(@Given n: Int): String = n.toString()

@Inject fun <A> given(@Given a: A): A = a

val result = <!CIRCULAR_CYCLE_ON_GIVEN_PROOF!>given<String>()<!>
