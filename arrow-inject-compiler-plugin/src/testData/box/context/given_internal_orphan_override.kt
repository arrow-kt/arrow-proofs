package foo.bar

import arrow.inject.annotations.Inject
import foo.bar.annotations.Given

data class Person(val name: String, val age: Int)

@Given val p1 = Person("Peter Parker", 22)

@Given internal val p2 = Person("Harry Potter", 14)

@Inject fun <A> given(@Given a: A): A = a

fun box(): String {
  val result = given<Person>()
  return if (result == Person("Harry Potter", 14)) {
    "OK"
  } else {
    "Fail: $result"
  }
}
