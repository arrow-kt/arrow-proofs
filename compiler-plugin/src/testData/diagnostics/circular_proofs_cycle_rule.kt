package foo.bar

import foo.bar.annotations.Config
import foo.bar.annotations.Given

<!CIRCULAR_CYCLE_ON_GIVEN_PROOF!>@Given internal fun n(@Given s: String): Int = s.toInt()<!>

<!CIRCULAR_CYCLE_ON_GIVEN_PROOF!>@Given internal fun s(@Given n: Int): String = n.toString()<!>

<!CIRCULAR_CYCLE_ON_GIVEN_PROOF!>@Given class SomeNumber(@Given private val string: SomeString)<!>

<!CIRCULAR_CYCLE_ON_GIVEN_PROOF!>@Given class SomeString(@Given private val number: SomeNumber)<!>

class AnotherNumber(private val string: AnotherString)

class AnotherString(private val number: AnotherNumber)

@Given class GivenNumber(@Config private val string: ConfigString)

@Config class ConfigString(private val number: GivenNumber)
