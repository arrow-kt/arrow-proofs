package arrow.inject.compiler.plugin.fir.resolution.checkers

import arrow.inject.compiler.plugin.fir.coneType
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.declarations.FirContextReceiver
import org.jetbrains.kotlin.fir.declarations.FirValueParameter
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.toConeTypeProjection
import org.jetbrains.kotlin.fir.types.type

fun FirElement.resolutionTargetType() =
  when (this) {
    is FirValueParameter -> coneType
    is FirContextReceiver -> typeRef.coneType
    is FirFunctionCall ->
      typeArguments[0].toConeTypeProjection().type!! // contextSyntheticFunction
    else -> error("unsupported $this")
  }
