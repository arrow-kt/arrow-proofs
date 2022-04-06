package io.arrow.proofs.sample

@Inject
internal class GetUserFake : GetUser {

  override operator fun invoke(id: UserId): User = User(id, "Fake")
}

//@Inject
//internal fun getUserFake(): GetUser = GetUserFake()
