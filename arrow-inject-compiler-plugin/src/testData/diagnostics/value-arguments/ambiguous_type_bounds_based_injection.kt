package foo.bar

import arrow.inject.annotations.Inject
import foo.bar.annotations.Given

@Given internal fun n(): Int = 42

@Given internal fun d(): Double = 33.0

@Inject fun t(@Given x: Number): Number = x

val result = <!AMBIGUOUS_PROOF_FOR_SUPERTYPE!>t()<!>
