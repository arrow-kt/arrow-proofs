package foo.bar

import arrow.inject.annotations.Inject
import foo.bar.annotations.Given

@Inject fun <A> given(@Given a: A): A = a

@Given class X(@Given val value: String)

val result = <!UNRESOLVED_GIVEN_CALL_SITE!>given<X>()<!>
