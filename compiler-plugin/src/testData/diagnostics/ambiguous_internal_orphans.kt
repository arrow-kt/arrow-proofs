package foo.bar

import arrow.inject.annotations.Inject
import foo.bar.annotations.Given

@Given internal fun n(): Int = 42

@Given internal fun n2(): Int = 0

@Inject fun <A> given(@Given a: A): A = a

val result = <!AMBIGUOUS_PROOF_FOR_SUPERTYPE!>given<Int>()<!>
