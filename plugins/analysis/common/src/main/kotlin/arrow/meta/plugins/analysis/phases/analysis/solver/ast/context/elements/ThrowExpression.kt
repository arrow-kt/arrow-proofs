package arrow.meta.plugins.analysis.phases.analysis.solver.ast.context.elements

interface ThrowExpression : Expression {
  val thrownExpression: Expression?
}