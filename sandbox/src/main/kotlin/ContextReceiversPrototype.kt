package foo.bar

import arrow.inject.annotations.context
import arrow.inject.annotations.contextual
import arrow.inject.annotations.Provider

@Provider internal fun intProvider(): Int = 42

data class Foo<A>(val n: A)

context(A)
@Provider internal fun <A> fooProvider(): Foo<A> = Foo(this@A)


fun f2(): Int {
  println("will drop from nested body")
  context<Foo<Int>>()
  return n
}

fun f3(): Int {
  println("will drop from nested body")
  return contextual<Int, Int>(intProvider()) {
    contextual<Foo<Int>, Int>(fooProvider<Int>()) {
      return n
    }
  }
}

fun box(): String {
  val n = f2()
  return if (n == 42) {
    "OK"
  } else {
    "Fail"
  }
}
