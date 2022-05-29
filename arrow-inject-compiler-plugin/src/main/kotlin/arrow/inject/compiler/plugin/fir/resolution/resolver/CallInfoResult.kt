package arrow.inject.compiler.plugin.fir.resolution.resolver

import arrow.inject.compiler.plugin.model.Proof
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.resolve.calls.CallInfo

internal sealed class CallInfoResult {
  data class Info(val callInfo: CallInfo) : CallInfoResult()
  data class FunctionCall(val call: FirFunctionCall) : CallInfoResult()
  data class CyclesFound(val proof: Proof) : CallInfoResult()
}
