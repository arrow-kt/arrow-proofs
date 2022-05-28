package foo.bar

import arrow.inject.annotations.Inject
import foo.bar.annotations.Config
import foo.bar.annotations.Given

@Given
object X {
  val value = "yes!"
}

@Config
object Y {
  val value = "nope!"
}

@Inject fun foo(id: Int, @Given x: X, @Config y: Y): String = "$id: ${x.value} to ${y.value}"

val result = foo(1)
