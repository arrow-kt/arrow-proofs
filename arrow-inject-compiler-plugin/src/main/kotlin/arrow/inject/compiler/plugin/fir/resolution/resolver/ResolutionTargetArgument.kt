package arrow.inject.compiler.plugin.fir.resolution.resolver

import org.jetbrains.kotlin.fir.declarations.FirContextReceiver
import org.jetbrains.kotlin.fir.declarations.FirValueParameter
import org.jetbrains.kotlin.fir.expressions.FirAnnotation

sealed class ResolutionTargetArgument {

  val annotations: List<FirAnnotation>
    get() =
      when (this) {
        is ContextReceiver -> contextReceiver.typeRef.annotations
        is ValueParameter -> valueParameter.annotations
      }

  data class ContextReceiver(val contextReceiver: FirContextReceiver) : ResolutionTargetArgument()

  data class ValueParameter(val valueParameter: FirValueParameter) : ResolutionTargetArgument()
}
