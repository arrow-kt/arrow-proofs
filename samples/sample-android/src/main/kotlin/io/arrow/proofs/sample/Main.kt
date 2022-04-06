package io.arrow.proofs.sample

val viewModel: UserViewModel = UserViewModel()

fun main() {
  println(viewModel.loadUser(UserId(2)))
}
