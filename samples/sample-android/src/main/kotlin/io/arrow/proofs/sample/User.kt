package io.arrow.proofs.sample

data class User(val id: UserId, val name: String)

@JvmInline
value class UserId(val value: Int)
