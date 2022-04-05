package io.arrow.proofs.sample

interface GetUser {
  suspend operator fun invoke(id: UserId): User
}
