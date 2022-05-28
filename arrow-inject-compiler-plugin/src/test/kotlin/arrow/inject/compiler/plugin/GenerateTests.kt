package arrow.inject.compiler.plugin

import arrow.inject.compiler.plugin.runners.AbstractBoxTest
import arrow.inject.compiler.plugin.runners.AbstractDiagnosticTest
import org.jetbrains.kotlin.generators.generateTestGroupSuiteWithJUnit5

fun main() {
  generateTestGroupSuiteWithJUnit5 {
    testGroup(testDataRoot = "src/testData", testsRoot = "src/testGenerated") {
      testClass<AbstractDiagnosticTest> {
        model("diagnostics/value-arguments")
        model("diagnostics/context-receivers")
      }

      testClass<AbstractBoxTest> {
        model("box/value-arguments")
        model("box/context-receivers")
      }
    }
  }
}
