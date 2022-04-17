package arrow.inject.compiler.plugin.fir

import arrow.inject.compiler.plugin.fir.utils.FirUtils
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.DeclarationCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirBasicDeclarationChecker
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirFunctionChecker
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.psi

class ProofsDeclarationCheckersExtension(
  session: FirSession,
) : FirAdditionalCheckersExtension(session), FirUtils {
  override fun FirDeclarationPredicateRegistrar.registerPredicates() {
    println("EXTENSION: ProofsDeclarationCheckersExtension")
    renderHasContextAnnotation()
  }

  //  override val declarationCheckers: DeclarationCheckers =
//    object : DeclarationCheckers() {
//
//      override val functionCheckers: Set<FirFunctionChecker> =
//        setOf(object : FirFunctionChecker() {
//          override fun check(
//            declaration: FirFunction,
//            context: CheckerContext,
//            reporter: DiagnosticReporter
//          ) {
//            println("functionCheckers")
//            println(declaration.psi?.text)
//          }
//
//        })
//      override val basicDeclarationCheckers: Set<FirBasicDeclarationChecker> =
//        setOf(
//          object : FirBasicDeclarationChecker() {
//            override fun check(
//              declaration: FirDeclaration,
//              context: CheckerContext,
//              reporter: DiagnosticReporter
//            ) {
//              println("basicDeclarationCheckers")
//              println(declaration.psi?.text)
//            }
//          }
//        )
//    }
}
