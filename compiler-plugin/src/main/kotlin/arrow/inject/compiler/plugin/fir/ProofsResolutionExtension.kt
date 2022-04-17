package arrow.inject.compiler.plugin.fir

import arrow.inject.compiler.plugin.fir.utils.FirUtils
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.FirExpressionResolutionExtension
import org.jetbrains.kotlin.fir.types.ConeKotlinType

class ProofsResolutionExtension(session: FirSession) :
  FirExpressionResolutionExtension(session), FirUtils {

  override fun FirDeclarationPredicateRegistrar.registerPredicates() {
    println("EXTENSION: ProofsResolutionExtension")
    renderHasContextAnnotation()
  }

  override fun addNewImplicitReceivers(functionCall: FirFunctionCall): List<ConeKotlinType> {
    //    println("FUNCTION CALL:")
    //    println(functionCall.psi?.text)

    return emptyList()
  }
}
