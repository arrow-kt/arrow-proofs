package io.arrow.proofs.sample

import org.junit.jupiter.api.Test

val viewModel: UserViewModel = inject()

class MainTest {

  @Test
  fun `replace getUserImpl with another impl`() {

    assert(viewModel.loadUser(UserId(1)).name == "Fake")
  }
}
