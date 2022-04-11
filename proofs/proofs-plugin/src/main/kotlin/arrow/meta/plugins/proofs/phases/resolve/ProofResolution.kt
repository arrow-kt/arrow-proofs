package arrow.meta.plugins.proofs.phases.resolve

import arrow.meta.phases.CompilerContext
import arrow.meta.phases.resolve.baseLineTypeChecker
import arrow.meta.plugins.proofs.phases.GivenProof
import arrow.meta.plugins.proofs.phases.Proof
import arrow.meta.plugins.proofs.phases.asProof
import arrow.meta.plugins.proofs.phases.isGivenContextProof
import arrow.meta.plugins.proofs.phases.resolve.scopes.ProofsScopeTower
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl
import org.jetbrains.kotlin.container.ContainerConsistencyException
import org.jetbrains.kotlin.container.get
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.Call
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtTypeArgumentList
import org.jetbrains.kotlin.psi.KtTypeProjection
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.LambdaArgument
import org.jetbrains.kotlin.psi.ValueArgument
import org.jetbrains.kotlin.resolve.BindingTrace
import org.jetbrains.kotlin.resolve.BindingTraceContext
import org.jetbrains.kotlin.resolve.calls.KotlinCallResolver
import org.jetbrains.kotlin.resolve.calls.components.InferenceSession
import org.jetbrains.kotlin.resolve.calls.components.KotlinResolutionCallbacks
import org.jetbrains.kotlin.resolve.calls.components.candidate.ResolutionCandidate
import org.jetbrains.kotlin.resolve.calls.context.BasicCallResolutionContext
import org.jetbrains.kotlin.resolve.calls.context.CheckArgumentTypesMode
import org.jetbrains.kotlin.resolve.calls.model.AllCandidatesResolutionResult
import org.jetbrains.kotlin.resolve.calls.model.CallResolutionResult
import org.jetbrains.kotlin.resolve.calls.model.GivenCandidate
import org.jetbrains.kotlin.resolve.calls.model.KotlinCall
import org.jetbrains.kotlin.resolve.calls.model.KotlinCallArgument
import org.jetbrains.kotlin.resolve.calls.model.KotlinCallComponents
import org.jetbrains.kotlin.resolve.calls.model.KotlinCallKind
import org.jetbrains.kotlin.resolve.calls.model.NoValueForParameter
import org.jetbrains.kotlin.resolve.calls.model.ReceiverKotlinCallArgument
import org.jetbrains.kotlin.resolve.calls.model.SimpleCandidateFactory
import org.jetbrains.kotlin.resolve.calls.model.TypeArgument
import org.jetbrains.kotlin.resolve.calls.model.checkCallInvariants
import org.jetbrains.kotlin.resolve.calls.smartcasts.DataFlowInfo
import org.jetbrains.kotlin.resolve.calls.smartcasts.DataFlowValueFactoryImpl
import org.jetbrains.kotlin.resolve.calls.tasks.ExplicitReceiverKind
import org.jetbrains.kotlin.resolve.calls.tower.CandidateWithBoundDispatchReceiver
import org.jetbrains.kotlin.resolve.calls.tower.ImplicitScopeTower
import org.jetbrains.kotlin.resolve.calls.tower.KnownResultProcessor
import org.jetbrains.kotlin.resolve.calls.tower.KotlinResolutionCallbacksImpl
import org.jetbrains.kotlin.resolve.calls.tower.PSICallResolver
import org.jetbrains.kotlin.resolve.calls.tower.TowerResolver
import org.jetbrains.kotlin.resolve.calls.tower.forceResolution
import org.jetbrains.kotlin.resolve.scopes.receivers.Receiver
import org.jetbrains.kotlin.resolve.scopes.receivers.ReceiverValue
import org.jetbrains.kotlin.resolve.scopes.receivers.ReceiverValueWithSmartCastInfo
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.UnwrappedType
import org.jetbrains.kotlin.types.expressions.ExpressionTypingContext
import org.jetbrains.kotlin.types.isError
import org.jetbrains.kotlin.types.typeUtil.isNothing
import org.jetbrains.kotlin.types.typeUtil.isTypeParameter
import org.jetbrains.kotlin.types.typeUtil.replaceArgumentsWithStarProjections

fun List<GivenProof>.matchingCandidates(
  compilerContext: CompilerContext,
  superType: KotlinType
): List<GivenProof> {
  val proofs =
    if (containsErrorsOrNothing(superType)) emptyList<GivenProof>()
    else {
      compilerContext.run {
        try {
          module?.run {
            componentProvider?.get<KotlinCallResolver>()?.let { proofsCallResolver ->
              val proofs =
                this@matchingCandidates.resolveGivenProofs(
                  superType,
                  compilerContext,
                  proofsCallResolver,
                  this
                )
              proofs
            }
          }
            ?: emptyList()
        } catch (e: ContainerConsistencyException) {
          emptyList<GivenProof>()
        }
      }
    }
  return proofs
}

fun List<GivenProof>.resolveGivenProofs(
  superType: KotlinType,
  compilerContext: CompilerContext,
  proofsCallResolver: KotlinCallResolver,
  psiCallResolver: PSICallResolver,
  moduleDescriptor: ModuleDescriptor
): List<GivenProof> {
  val scopeTower = ProofsScopeTower(moduleDescriptor, this, compilerContext)
  val callResolutionResult =
    proofsCallResolver.run {
      resolveAndCompleteGivenCandidates(
        scopeTower = scopeTower,
        expectedType = superType.unwrap(),
        collectAllCandidates = true,
        givenCandidates = map { it.givenCandidate()  },
        kotlinCall = TODO(),
        resolutionCallbacks = psiCallResolver.crea
        )
      )
    }
  return callResolutionResult.matchingGivenProofs(superType)
}

private fun GivenProof.givenCandidate(
  candidateFactory: SimpleCandidateFactory,
): ResolutionCandidate {
  // TODO this looks the ok part, still flacky in the IR passing these extension and receiver
  val dispatchReceiver = (through.containingDeclaration as? CallableDescriptor)?.dispatchReceiverParameter
  val extensionReceiver = (through.containingDeclaration as? CallableDescriptor)?.extensionReceiverParameter

  return if (through.containingDeclaration != null) {
    val kind = when {
      dispatchReceiver != null && extensionReceiver != null -> ExplicitReceiverKind.BOTH_RECEIVERS
      dispatchReceiver != null -> ExplicitReceiverKind.DISPATCH_RECEIVER
      extensionReceiver != null -> ExplicitReceiverKind.EXTENSION_RECEIVER
      else -> ExplicitReceiverKind.NO_EXPLICIT_RECEIVER
    }

    candidateFactory.createCandidate(
      towerCandidate = CandidateWithBoundDispatchReceiver(dispatchReceiver?.let { ReceiverValueWithSmartCastInfo(it.value, emptySet(), false) }, callableDescriptor, emptyList()),
      explicitReceiverKind = kind,
      extensionReceiver = extensionReceiver?.let { ReceiverValueWithSmartCastInfo(it.value, emptySet(), true) },
    )
  } else {
    candidateFactory.createCandidate(
      towerCandidate = CandidateWithBoundDispatchReceiver(null, callableDescriptor, emptyList()),
      explicitReceiverKind = ExplicitReceiverKind.NO_EXPLICIT_RECEIVER,
      extensionReceiver = null
    )
  }
}

fun containsErrorsOrNothing(vararg types: KotlinType) = types.any { it.isError || it.isNothing() }

inline fun <reified A : GivenProof> CallResolutionResult.matchingGivenProofs(
  superType: KotlinType
): List<A> =
  if (this is AllCandidatesResolutionResult) {
    // TODO if candidate diagnostics includes NoValueForParameter then we may want to proceed to
    // inductive resolution
    // if the param was a contextual param
    val selectedCandidates =
      allCandidates.filter {
        val missingParams = it.diagnostics.firstOrNull()
        it.diagnostics.isEmpty() ||
          // this is a provider with contextual arguments
          missingParams is NoValueForParameter &&
            missingParams.parameterDescriptor.annotations.any { it.isGivenContextProof() }
      }
    val proofs =
      selectedCandidates
        .flatMap { it.candidate.resolvedCall.candidateDescriptor.asProof().asIterable() }
        .filter { it is A && includeInCandidates(it.to, superType) }
        .filterIsInstance<A>()
    proofs.toList()
  } else emptyList()

fun includeInCandidates(a: KotlinType, b: KotlinType): Boolean =
  (a.isTypeParameter() ||
    baseLineTypeChecker.isSubtypeOf(
      a.replaceArgumentsWithStarProjections(),
      b.replaceArgumentsWithStarProjections()
    ))

fun kotlinCall(
  callKind: KotlinCallKind,
  explicitReceiver: ReceiverKotlinCallArgument?
): KotlinCall =
  object : KotlinCall {
    override val argumentsInParenthesis: List<KotlinCallArgument>
      get() {
        return emptyList()
      }
    override val callKind: KotlinCallKind = callKind
    override val explicitReceiver: ReceiverKotlinCallArgument? = explicitReceiver
    override val externalArgument: KotlinCallArgument? = null
    override val isForImplicitInvoke: Boolean = false
    override val name: Name = Name.identifier("Proof type-checking and resolution")
    override val typeArguments: List<TypeArgument> = emptyList()
    override val dispatchReceiverForInvokeExtension: ReceiverKotlinCallArgument? = null
  }
