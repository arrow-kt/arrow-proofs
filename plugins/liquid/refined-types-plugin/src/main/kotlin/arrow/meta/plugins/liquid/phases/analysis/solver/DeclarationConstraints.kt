package arrow.meta.plugins.liquid.phases.analysis.solver

import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.sosy_lab.java_smt.api.BooleanFormula

data class DeclarationConstraints(
  val descriptor: DeclarationDescriptor,
  val pre: List<BooleanFormula>,
  val post: List<BooleanFormula>
)