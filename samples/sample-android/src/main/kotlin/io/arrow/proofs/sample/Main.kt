package io.arrow.proofs.sample

suspend fun main() {
  val viewModel: UserViewModel = inject()
  viewModel.loadUser(UserId(2))
}
