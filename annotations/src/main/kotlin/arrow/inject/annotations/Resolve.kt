package arrow.inject.annotations

@Retention(AnnotationRetention.RUNTIME)
@Target(
  AnnotationTarget.CLASS,
  AnnotationTarget.FUNCTION,
)
@MustBeDocumented
public annotation class Resolve
