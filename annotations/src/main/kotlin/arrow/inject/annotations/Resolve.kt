package arrow.inject.annotations

@Retention(AnnotationRetention.RUNTIME)
@Target(
  AnnotationTarget.CLASS,
  AnnotationTarget.FUNCTION,
)
@MustBeDocumented
public annotation class Resolve

private class ResolveException(override val message: String?) : Throwable()

@Resolve public fun <A> resolve(): A = throw ResolveException("Compile time replacement")
