package foo.bar

import arrow.inject.annotations.Inject

@Given fun stringProvider(): String = "yes"

@Inject fun foo(id: Int, @Given x: String): String = "$id: $x"

//val result = foo(1)
