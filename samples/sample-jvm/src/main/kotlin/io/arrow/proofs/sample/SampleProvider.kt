package io.arrow.proofs.sample

@Inject
class SampleClassProvider {

  fun hello(): String = "Hi class"
}

@Inject
object SampleObjectProvider {

  fun hello(): String = "Hi object"
}

@Inject internal fun sampleFunProviderHello() = "Hi fun"

@Inject internal val sampleValProviderNumber = 1
