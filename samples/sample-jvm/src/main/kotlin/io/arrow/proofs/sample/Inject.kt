package io.arrow.proofs.sample

import arrow.Context

@Context annotation class Inject

inline fun <A> inject(@Inject identity: A): A = identity
