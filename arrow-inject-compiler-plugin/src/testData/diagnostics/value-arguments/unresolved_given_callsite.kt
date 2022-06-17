package foo.bar

import arrow.inject.annotations.Inject
import foo.bar.annotations.Given

@Inject fun <A> given(@Given a: A): A = a

val result = <!UNRESOLVED_GIVEN_CALL_SITE!>given<String>()<!>
