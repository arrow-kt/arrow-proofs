package foo.bar

import arrow.inject.annotations.context
import arrow.inject.annotations.Provider

@Provider internal fun intProvider(): Int = 42

data class Foo<A>(val n: A)

context(A)
@Provider internal fun <A> fooProvider(): Foo<A> = Foo(this@A)

fun box(): String {
  context<Foo<Int>>()
  return if (n == 42) {
    "OK"
  } else {
    "Fail"
  }
}
