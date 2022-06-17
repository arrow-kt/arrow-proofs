package arrow.inject.compiler.plugin.fir.resolution.checkers.type

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.types.FirTypeRef

interface FirAbstractTypeChecker {

  fun check(typeRef: FirTypeRef, context: CheckerContext, reporter: DiagnosticReporter)
}
