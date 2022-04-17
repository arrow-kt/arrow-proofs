package arrow.inject.compiler.plugin.fir

import arrow.inject.compiler.plugin.fir.utils.FirUtils
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.FirTypeAttributeExtension
import org.jetbrains.kotlin.fir.types.ConeAttribute

class ProofsTypeAttributeExtension(session: FirSession) :
  FirTypeAttributeExtension(session), FirUtils {
  override fun convertAttributeToAnnotation(attribute: ConeAttribute<*>): FirAnnotation? {
    return null
  }

  override fun extractAttributeFromAnnotation(annotation: FirAnnotation): ConeAttribute<*>? {
    return null
  }


  override fun FirDeclarationPredicateRegistrar.registerPredicates() {
    println("EXTENSION: ProofsTypeAttributeExtension")
    renderHasContextAnnotation()
  }
}
