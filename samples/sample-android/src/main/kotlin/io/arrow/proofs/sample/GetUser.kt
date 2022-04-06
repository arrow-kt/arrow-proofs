package io.arrow.proofs.sample

interface GetUser {
  operator fun invoke(id: UserId): User
}
