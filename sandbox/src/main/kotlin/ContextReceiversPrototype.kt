package foo.bar

annotation class Provider

fun <A> resolve(a: context(Nothing)() -> A) = TODO()
fun <A, B> with() = TODO()


@Provider
class Persistence

interface Repo

context(Persistence)
@Provider class Repo1 : Repo

context(Repo, Repo2, Repo3, Repo4)
@Provider class ViewModel(val id: Int)

fun main2() {
//  context<Repo>() // context(Repo, Persistence)
//  context<Repo2>() // context(Repo, Persistence)
//  context<Repo3>() // context(Repo, Persistence)
//  context<Repo4>() // context(Repo, Persistence)


//  contextOf<ViewModel>() // context(Repo, Repo2, Repo3, Repo4, Persistence)

//  @Resolve val viewModel: ViewModel = ViewModel(1)

  //  val repo: Repo = @Resolve Repo1(1)
}
