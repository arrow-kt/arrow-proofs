package foo.bar

import arrow.inject.annotations.Inject
import foo.bar.annotations.Given

data class Person(val name: String, val age: Int)

@Given fun person1(): Person = Person("X", 0)

@Given fun person2(): Person = Person("X", 0)

@Given internal fun person3(): Person = Person("X", 0)

@Given internal fun person4(): Person = Person("X", 0)

@Inject fun <A> given(@Given a: A): A = a

val result = <!AMBIGUOUS_PROOF_FOR_SUPERTYPE!>given<Person>()<!>
