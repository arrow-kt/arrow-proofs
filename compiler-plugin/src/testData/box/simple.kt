package foo.bar

import arrow.inject.annotations.Context
import arrow.inject.annotations.Inject
import arrow.inject.annotations.Resolve

class ResolveException : Throwable()

@Resolve
<!NOTHING_TO_INLINE!>inline<!> fun <A> resolve(): A = throw ResolveException()

@Context
@Retention(AnnotationRetention.RUNTIME)
@Target(
  AnnotationTarget.CLASS,
  AnnotationTarget.FUNCTION,
  AnnotationTarget.PROPERTY,
  AnnotationTarget.VALUE_PARAMETER
)
@MustBeDocumented
annotation class Given

@Context
@Retention(AnnotationRetention.RUNTIME)
@Target(
  AnnotationTarget.CLASS,
  AnnotationTarget.FUNCTION,
  AnnotationTarget.PROPERTY,
  AnnotationTarget.VALUE_PARAMETER
)
@MustBeDocumented
annotation class Config

@Given object X {
  val value = "yes!"
}

@Config object Y {
  val value = "nope!"
}

@Inject
fun foo(id: Int, @Given x: X, @Config y: Y): String = "$id: ${x.value} to ${y.value}"

fun box(): String {
  val result = foo(1)
  return if (result == "1: yes! to nope!") { "OK" } else { "Fail: $result" }
}
