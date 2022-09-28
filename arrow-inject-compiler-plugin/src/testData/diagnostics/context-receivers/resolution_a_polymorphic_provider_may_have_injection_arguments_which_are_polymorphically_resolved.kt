package foo.bar

import arrow.inject.annotations.context
import arrow.inject.annotations.ContextResolution
import arrow.inject.annotations.Contextual

@Contextual internal fun intProvider(): Int = 42

data class Foo<A>(val n: A)

context(A)
@Contextual internal fun <A> fooProvider(): Foo<A> = Foo(this@A)

context(Foo<Int>)
@ContextResolution
fun main() {
  val number: Int = n
}
