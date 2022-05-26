package foo.bar

annotation class Provider

fun <A> resolve(a: context(Nothing)() -> A) = TODO()
fun <A, B> with() = TODO()


@Provider
class Persistence

interface Repo

context(Persistence)
@Provider class Repo1 : Repo

context(Persistence)
@Provider class Repo2 : Repo

context(Persistence)
@Provider class Repo3 : Repo

context(Persistence)
@Provider class Repo4 : Repo

context(Repo1, Repo2, Repo3, Repo4)
@Provider class ViewModel(val id: Int)

fun main2() {
//  context<Repo1>() // context(Repo1, Persistence)
//  context<Repo2>() // context(Repo2, Persistence)
//  context<Repo3>() // context(Repo3, Persistence)
//  context<Repo4>() // context(Repo4, Persistence)

  contextOf<ViewModel>() // context(Repo1, Repo2, Repo3, Repo4, Persistence)

  val viewModel: ViewModel = ViewModel(1)
}
