package foo.bar

import foo.bar.annotations.Given

<!CYCLE_ON_GIVEN_PROOF!>@Given internal fun n(@Given s: String): Int = s.toInt()<!>

<!CYCLE_ON_GIVEN_PROOF!>@Given internal fun s(@Given n: Int): String = n.toString()<!>

<!CYCLE_ON_GIVEN_PROOF!>@Given class SomeNumber(@Given string: SomeString)<!>

<!CYCLE_ON_GIVEN_PROOF!>@Given class SomeString(@Given number: SomeNumber)<!>
