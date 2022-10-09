package arrow.inject.annotations

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

@OptIn(ExperimentalContracts::class)
inline fun <T, R> contextual(ev: T, f: T.() -> R): R {
  contract {
    callsInPlace(f, InvocationKind.EXACTLY_ONCE)
  }
  return with(ev, f)
}
