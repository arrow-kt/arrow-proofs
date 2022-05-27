package arrow.inject.annotations

inline fun <T, R> contextual(ev: T, f: T.() -> R): R =
  ev.run(f)
