package foo.bar

import arrow.inject.annotations.Contextual
import arrow.inject.annotations.ContextResolution

@Contextual internal fun intProvider(): Int = 42

data class Foo<A>(val n: A)

context(A)
@Contextual internal fun <A> fooProvider(): Foo<A> = Foo(this@A)

@ContextResolution
fun box(): String {
  return if (n == 42) {
    "OK"
  } else {
    "Fail"
  }
}
