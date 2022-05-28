package arrow.inject.annotations

inline fun <T, R> contextual(ev: T, f: T.() -> R): R =
  with(ev, f)
