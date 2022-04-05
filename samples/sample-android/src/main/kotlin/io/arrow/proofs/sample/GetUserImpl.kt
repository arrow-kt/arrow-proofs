package io.arrow.proofs.sample

class GetUserImpl(private val database: Database) : GetUser {

  override suspend operator fun invoke(id: UserId): User = database.getUser(id)
}

@Inject fun getUserImpl(@Inject database: Database): GetUser = GetUserImpl(database)
