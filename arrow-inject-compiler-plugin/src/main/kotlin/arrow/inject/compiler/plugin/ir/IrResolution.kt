package arrow.inject.compiler.plugin.ir

import arrow.inject.compiler.plugin.model.Proof
import arrow.inject.compiler.plugin.model.ProofAnnotationsFqName
import arrow.inject.compiler.plugin.model.ProofResolution
import arrow.inject.compiler.plugin.model.asProofCacheKey
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.fir.declarations.FirMemberDeclaration
import org.jetbrains.kotlin.fir.declarations.utils.visibility
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.descriptors.toIrBasedKotlinType
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionAccessExpression
import org.jetbrains.kotlin.ir.expressions.IrMemberAccessExpression
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrValueParameterSymbol
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.util.getPackageFragment
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

interface IrResolution : IrPluginContext, ProofsIrAbstractCodegen {

  val contextualFunction: IrSimpleFunctionSymbol
    get() = topLevelFunctionSymbol("arrow.inject.annotations", "contextual")

  fun IrFunction.mirrorFunction(): IrSimpleFunction? {
    val packageFqName = getPackageFragment().fqName
    val matchingFunctions = referenceFunctions(CallableId(packageFqName, name))
    return matchingFunctions
      .firstOrNull {
        it.owner.hasAnnotation(ProofAnnotationsFqName.ContextResolutionAnnotation)
      }
      ?.owner
  }

  fun <D> withFunReceiverParameter(
    dec: D,
    targetType: IrType,
    paramSymbol: IrValueParameterSymbol
  ): IrValueParameter where D : IrDeclaration, D : IrDeclarationParent =
    irFactory
      .createValueParameter(
        startOffset = UNDEFINED_OFFSET,
        endOffset = UNDEFINED_OFFSET,
        origin = IrDeclarationOrigin.DEFINED,
        symbol = paramSymbol,
        name = Name.identifier("${'$'}this${'$'}contextual"),
        index = -1,
        type = targetType,
        varargElementType = null,
        isAssignable = false,
        isCrossinline = false,
        isHidden = false,
        isNoinline = false
      )
      .also { it.parent = dec }

  fun contextProofCall(irType: IrType): IrExpression? =
    proofCache
      .getProofFromCache(
        irType.toIrBasedKotlinType().asProofCacheKey(ProofAnnotationsFqName.ContextualAnnotation)
      )
      ?.let { proofResolution -> substitutedResolvedContextCall(proofResolution, irType) }

  private fun substitutedResolvedContextCall(
    proofResolution: ProofResolution,
    irType: IrType
  ): IrExpression? {
    val proof = proofResolution.proof
    val ambiguousProofs = proofResolution.ambiguousProofs
    val internalProof =
      ambiguousProofs.firstOrNull {
        (it.declaration as? FirMemberDeclaration)?.visibility == Visibilities.Internal
      }
    return if (proof != null) substitutedContextProofCall(internalProof ?: proof, irType) else null
  }

  fun substitutedContextProofCall(proof: Proof, irType: IrType): IrExpression {
    val proofIrDeclaration = proof.irDeclaration() as? IrFunction
    return matchedContextCandidateProofCall(
      declaration = proof.irDeclaration(),
      typeSubstitutor =
      IrTypeSubstitutor(
        proofIrDeclaration?.typeParameters.orEmpty().map { it.symbol },
        irType.getArguments().filterIsInstance<IrTypeArgument>(),
        irBuiltIns
      )
    )
  }

  fun matchedContextCandidateProofCall(
    declaration: IrDeclaration,
    typeSubstitutor: IrTypeSubstitutor
  ): IrExpression {
    return declaration.irCall().apply {
      if (this is IrMemberAccessExpression<*>) {
        if (declaration is IrTypeParametersContainer) {
          declaration.typeParameters.forEachIndexed { index, typeParam ->
            val newType = typeSubstitutor.substitute(typeParam.defaultType)
            putTypeArgument(index, newType)
          }
        }

        if (declaration is IrFunction) {
          declaration.valueParameters.forEachIndexed { index, valueParameter ->
            val contextFqName: FqName? =
              valueParameter.metaContextAnnotations.firstOrNull()?.type?.classFqName
            if (contextFqName != null) {
              val newType = typeSubstitutor.substitute(valueParameter.type)
              val argumentProvedExpression = contextProofCall(newType)
              if (argumentProvedExpression != null) {
                putValueArgument(index, argumentProvedExpression)
              }
            }
          }
        }
      }
      this.type = typeSubstitutor.substitute(this.type)
    }
  }

  fun getAllContextReceiversTypes(
    irType: IrType?,
    previousIrTypes: MutableList<IrType>,
  ): List<IrType> {
    if (irType != null) {
      previousIrTypes.add(irType)
      val expression = contextProofCall(irType)
      if (expression is IrFunctionAccessExpression) {
        expression.symbol.owner.contextReceiversValueParameters.flatMap { param ->
          val targetType = targetType(irType, param.type)
          val resolvedType = targetType ?: param.type
          getAllContextReceiversTypes(resolvedType, previousIrTypes)
        }
      }
    }
    return previousIrTypes.reversed()
  }
}
