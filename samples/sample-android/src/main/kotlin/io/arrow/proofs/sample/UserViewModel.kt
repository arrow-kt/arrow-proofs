package io.arrow.proofs.sample

@Inject
class UserViewModel(@Inject private val getUser: GetUser) {

  fun loadUser(id: UserId): User = getUser(id)
}
