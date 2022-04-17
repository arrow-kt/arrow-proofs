package foo.bar

import arrow.inject.annotations.Context
import arrow.inject.annotations.Inject
import arrow.inject.annotations.Resolve

class ResolveException : Throwable()

@Resolve
inline fun resolve(): Nothing = throw ResolveException()

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
fun foo(@Given x: X, @Config y: Y): String = "${x.value} to ${y.value}"

val result = foo()
