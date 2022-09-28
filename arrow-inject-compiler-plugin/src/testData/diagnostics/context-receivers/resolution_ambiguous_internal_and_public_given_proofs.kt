package foo.bar

import arrow.inject.annotations.Contextual
import arrow.inject.annotations.ContextResolution
import arrow.inject.annotations.context

data class Person(val name: String, val age: Int)

@Contextual fun person1(): Person = Person("X", 0)

@Contextual fun person2(): Person = Person("X", 0)

@Contextual internal fun person3(): Person = Person("X", 0)

@Contextual internal fun person4(): Person = Person("X", 0)

context(<!AMBIGUOUS_PROOF_FOR_SUPERTYPE!>Person<!>)
@ContextResolution
fun main() {

}
