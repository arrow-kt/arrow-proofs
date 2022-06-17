package arrow.inject.annotations

@Target(
  AnnotationTarget.EXPRESSION,
  AnnotationTarget.LOCAL_VARIABLE,
)
@Retention(AnnotationRetention.SOURCE)
annotation class Resolve

@CompileTime
fun <A> context(a: A? = null) = Unit

@CompileTime
fun <A, B> context(a: A? = null, b: B? = null) = Unit

@CompileTime
fun <A, B, C> context(a: A? = null, b: B? = null, c: C? = null) = Unit

@CompileTime
fun <A, B, C, D> context(a: A? = null, b: B? = null, c: C? = null, d: D? = null) = Unit

@CompileTime
fun <A, B, C, D, E> context(a: A? = null, b: B? = null, c: C? = null, d: D? = null, e: E? = null) = Unit
