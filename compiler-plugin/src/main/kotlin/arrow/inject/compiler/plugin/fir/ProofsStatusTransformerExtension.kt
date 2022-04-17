package arrow.inject.compiler.plugin.fir

import arrow.inject.compiler.plugin.fir.utils.FirUtils
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.FirStatusTransformerExtension
import org.jetbrains.kotlin.fir.psi

class ProofsStatusTransformerExtension(session: FirSession) :
  FirStatusTransformerExtension(session), FirUtils {

  override fun needTransformStatus(declaration: FirDeclaration): Boolean =
    false.also {
      println("DECLARATION")
      println(declaration.psi?.text)
    }



  override fun FirDeclarationPredicateRegistrar.registerPredicates() {
    println("EXTENSION: ProofsStatusTransformerExtension")
    renderHasContextAnnotation()
  }
}
