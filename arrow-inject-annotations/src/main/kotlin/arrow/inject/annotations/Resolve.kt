package arrow.inject.annotations

@Target(
  AnnotationTarget.EXPRESSION,
  AnnotationTarget.LOCAL_VARIABLE,
)
@Retention(AnnotationRetention.SOURCE)
annotation class Resolve

fun <A> contextOf() = Unit
