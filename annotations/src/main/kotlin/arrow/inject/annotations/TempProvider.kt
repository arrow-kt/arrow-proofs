// TODO: remove this file

package arrow.inject.annotations

@Context
@Retention(AnnotationRetention.RUNTIME)
@Target(
  AnnotationTarget.CLASS,
  AnnotationTarget.FUNCTION,
  AnnotationTarget.PROPERTY,
  AnnotationTarget.VALUE_PARAMETER
)
@MustBeDocumented
annotation class Temp

@Temp fun baz(): String = "Baz"

@Temp object Bazz

@Temp val barr: String = "heee"

class Bar {
  @Temp val hello: String = "hello"
}

@Temp
class Barrrrr {

}
