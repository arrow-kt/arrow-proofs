package foo.bar

import arrow.inject.annotations.Inject
import foo.bar.annotations.Given

@Inject fun <A> given(@Given a: A): A = a

<!OWNERSHIP_VIOLATED_PROOF!>@Given fun n(): Int = 42<!>

val x = given<Int>()
