package io.arrow.proofs.sample

fun main() {
  val sampleClassProvider: SampleClassProvider = inject()
  println(sampleClassProvider.hello())

  val sampleObjectClassProvider: SampleObjectProvider = inject()
  println(sampleObjectClassProvider.hello())

  val sampleFunProvider: String = inject()
  println(sampleFunProvider)

  val sampleValProvider: Int = inject()
  println(sampleValProvider)
}
