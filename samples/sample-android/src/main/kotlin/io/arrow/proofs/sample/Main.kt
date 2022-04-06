package io.arrow.proofs.sample

val viewModel: UserViewModel = inject()

fun main() {
  println(viewModel.loadUser(UserId(2)))
}
