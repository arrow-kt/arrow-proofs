package foo.bar

import arrow.inject.annotations.Provider
import arrow.inject.annotations.context

data class Person(val name: String, val age: Int)

@Provider fun person1(): Person = Person("X", 0)

@Provider fun person2(): Person = Person("X", 0)

@Provider internal fun person3(): Person = Person("X", 0)

@Provider internal fun person4(): Person = Person("X", 0)

fun main() {
  <!AMBIGUOUS_PROOF_FOR_SUPERTYPE!>context<Person>()<!>
}
