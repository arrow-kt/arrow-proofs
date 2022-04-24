//package arrow.inject.compiler.plugin.fir
//
//import arrow.inject.compiler.plugin.fir.utils.FirUtils
//import arrow.inject.compiler.plugin.proof.Proof
//import java.util.concurrent.atomic.AtomicInteger
//import org.jetbrains.kotlin.fir.FirOverloadByLambdaReturnTypeResolver
//import org.jetbrains.kotlin.fir.FirSession
//import org.jetbrains.kotlin.fir.declarations.FirResolvePhase
//import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
//import org.jetbrains.kotlin.fir.expressions.FirFunctionCallOrigin
//import org.jetbrains.kotlin.fir.expressions.FirImplicitInvokeCall
//import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccessExpression
//import org.jetbrains.kotlin.fir.expressions.buildResolvedArgumentList
//import org.jetbrains.kotlin.fir.expressions.builder.buildFunctionCall
//import org.jetbrains.kotlin.fir.references.builder.buildResolvedNamedReference
//import org.jetbrains.kotlin.fir.resolve.ScopeSession
//import org.jetbrains.kotlin.fir.resolve.calls.CallInfo
//import org.jetbrains.kotlin.fir.resolve.calls.CallKind
//import org.jetbrains.kotlin.fir.resolve.calls.Candidate
//import org.jetbrains.kotlin.fir.resolve.calls.CandidateFactory
//import org.jetbrains.kotlin.fir.resolve.calls.ConeCallConflictResolver
//import org.jetbrains.kotlin.fir.resolve.calls.FirNamedReferenceWithCandidate
//import org.jetbrains.kotlin.fir.resolve.calls.callConflictResolverFactory
//import org.jetbrains.kotlin.fir.resolve.calls.tower.FirTowerResolver
//import org.jetbrains.kotlin.fir.resolve.calls.tower.TowerGroup
//import org.jetbrains.kotlin.fir.resolve.inference.inferenceComponents
//import org.jetbrains.kotlin.fir.resolve.transformers.StoreNameReference
//import org.jetbrains.kotlin.fir.resolve.transformers.body.resolve.FirBodyResolveTransformer
//import org.jetbrains.kotlin.fir.types.ConeKotlinType
//import org.jetbrains.kotlin.fir.types.isNothing
//import org.jetbrains.kotlin.fir.types.toFirResolvedTypeRef
//import org.jetbrains.kotlin.name.Name
//import org.jetbrains.kotlin.resolve.calls.results.TypeSpecificityComparator
//import org.jetbrains.kotlin.resolve.calls.tasks.ExplicitReceiverKind
//import org.jetbrains.kotlin.resolve.calls.tower.CandidateApplicability
//import org.jetbrains.kotlin.resolve.calls.tower.isSuccess
//
//class ProofResolver(override val session: FirSession) : FirUtils {
//
//  fun List<Proof>.matchingCandidates(
//    expression: FirQualifiedAccessExpression,
//    type: ConeKotlinType
//  ): Set<Candidate> {
//    val candidates: Set<Candidate> =
//      if (type.isNothing) {
//        emptySet()
//      } else {
//        firTowerResolver.reset()
//
//        val qualifiedAccess = buildFunctionCall {
//          typeRef = type.toFirResolvedTypeRef()
//          argumentList = buildResolvedArgumentList(LinkedHashMap())
//          calleeReference = buildResolvedNamedReference {
//            name = resolve.name
//            resolvedSymbol = resolve.symbol
//          }
//        }
//
//        val info =
//          CallInfo(
//            callSite = qualifiedAccess,
//            callKind =
//              if (qualifiedAccess is FirFunctionCall) CallKind.Function
//              else CallKind.VariableAccess,
//            name = Name.identifier("Proof resolution call"),
//            explicitReceiver = null, // TODO()
//            argumentList = buildResolvedArgumentList(LinkedHashMap()),
//            isImplicitInvoke = qualifiedAccess is FirImplicitInvokeCall,
//            typeArguments = qualifiedAccess.typeArguments,
//            session = session,
//            containingFile = fakeFirFile,
//            containingDeclarations = this.map { it.declaration },
//            origin = FirFunctionCallOrigin.Regular, // TODO()
//          )
//
//        val context = firBodyResolveTransformer.resolutionContext
//
//        val result = firTowerResolver.runResolver(info, context)
//
//        val candidateFactory = CandidateFactory(context, info)
//
//        this.forEach {
//          result.consumeCandidate(
//            TowerGroup.Member,
//            candidateFactory.createCandidate(
//              callInfo = info,
//              symbol = it.declaration.symbol,
//              explicitReceiverKind = ExplicitReceiverKind.NO_EXPLICIT_RECEIVER, // TODO()
//              scope = null, // TODO()
//              dispatchReceiverValue = null, // TODO()
//              givenExtensionReceiverOptions = emptyList()
//            ),
//            context
//          )
//        }
//
//        val bestCandidates = result.bestCandidates()
//
//        fun chooseMostSpecific(): Set<Candidate> {
//          return conflictResolver.chooseMaximallySpecificCandidates(
//            bestCandidates,
//            discriminateGenerics = true,
//            discriminateAbstracts = false
//          )
//        }
//
//        var reducedCandidates =
//          if (!result.currentApplicability.isSuccess) {
//            val distinctApplicabilities =
//              bestCandidates.mapTo(mutableSetOf()) { it.currentApplicability }
//            if (distinctApplicabilities.size == 1 &&
//                distinctApplicabilities.single() > CandidateApplicability.INAPPLICABLE
//            ) {
//              chooseMostSpecific()
//            } else {
//              bestCandidates.toSet()
//            }
//          } else {
//            chooseMostSpecific()
//          }
//
//        reducedCandidates =
//          overloadByLambdaReturnTypeResolver.reduceCandidates(
//            qualifiedAccess,
//            bestCandidates,
//            reducedCandidates
//          )
//
//        val nameReference = createResolvedNamedReference(
//          functionCall.calleeReference,
//          name,
//          result.info,
//          result.candidates,
//          result.applicability,
//          functionCall.explicitReceiver,
//          expectedCallKind = if (forceCandidates != null) CallKind.VariableAccess else null,
//          expectedCandidates = forceCandidates
//        )
//
//        val resultExpression = functionCall.transformCalleeReference(StoreNameReference, nameReference)
//        val candidate = (nameReference as? FirNamedReferenceWithCandidate)?.candidate
//
//        reducedCandidates
//      }
//    return candidates
//  }
//
//  private val firTowerResolver: FirTowerResolver by lazy {
//    FirTowerResolver(
//      firBodyResolveTransformer.components,
//      firBodyResolveTransformer.components.resolutionStageRunner,
//    )
//  }
//
//  private val firBodyResolveTransformer: FirBodyResolveTransformer by lazy {
//    FirBodyResolveTransformer(
//      session = session,
//      phase = FirResolvePhase.BODY_RESOLVE,
//      implicitTypeOnly = false,
//      scopeSession = scopeSession,
//    )
//  }
//
//  private val scopeSession = ScopeSession()
//
//  override val counter: AtomicInteger = AtomicInteger()
//
//  private val conflictResolver: ConeCallConflictResolver by lazy {
//    session.callConflictResolverFactory.create(
//      TypeSpecificityComparator.NONE,
//      session.inferenceComponents
//    )
//  }
//
//  private val overloadByLambdaReturnTypeResolver by lazy {
//    FirOverloadByLambdaReturnTypeResolver(firBodyResolveTransformer.components)
//  }
//}
