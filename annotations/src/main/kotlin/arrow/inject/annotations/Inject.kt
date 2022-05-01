package arrow.inject.annotations

@Retention(AnnotationRetention.RUNTIME)
@Target(
  AnnotationTarget.CLASS,
  AnnotationTarget.FUNCTION,
  AnnotationTarget.CONSTRUCTOR,
)
@MustBeDocumented
public annotation class Inject
