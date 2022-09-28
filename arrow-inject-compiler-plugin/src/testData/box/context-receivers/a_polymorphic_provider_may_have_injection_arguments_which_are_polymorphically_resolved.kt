package foo.bar

import arrow.inject.annotations.Contextual
import arrow.inject.annotations.ContextResolution

@Contextual internal fun intProvider(): Int = 42

data class Foo<A>(val n: A)

context(A)
@Contextual internal fun <A> fooProvider(): Foo<A> = Foo(this@A)

@ContextResolution
fun f2(): Int {
  println("will drop from nested body")
  return n
}

fun box(): String {
  val n = f2()
  return if (n == 42) {
    "OK"
  } else {
    "Fail"
  }
}
