package io.arrow.proofs.sample

@Inject
class GetUserImpl(@Inject private val database: Database) : GetUser {

  override operator fun invoke(id: UserId): User = database.getUser(id)
}
