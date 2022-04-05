package io.arrow.proofs.sample

@Inject
object Database {

  fun getUser(id: UserId): User = User(id, "Javi")
}
