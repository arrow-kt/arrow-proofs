@file:OptIn(SymbolInternals::class)

package arrow.inject.compiler.plugin.fir

import arrow.inject.compiler.plugin.model.ProofAnnotationsFqName
import org.jetbrains.kotlin.fir.FirAnnotationContainer
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.toClassLikeSymbol
import org.jetbrains.kotlin.fir.backend.Fir2IrSignatureComposer
import org.jetbrains.kotlin.fir.backend.jvm.FirJvmKotlinMangler
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirSimpleFunction
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.extensions.predicate.DeclarationPredicate
import org.jetbrains.kotlin.fir.extensions.predicate.has
import org.jetbrains.kotlin.fir.resolve.fqName
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.signaturer.FirBasedSignatureComposer
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.ir.util.IdSignature
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

internal interface FirAbstractProofComponent {

  val session: FirSession

  val composer: Fir2IrSignatureComposer
    get() = FirBasedSignatureComposer(FirJvmKotlinMangler(session))

  val contextPredicate: DeclarationPredicate
    get() = has(ProofAnnotationsFqName.ContextAnnotation)

  val injectPredicate: DeclarationPredicate
    get() = has(ProofAnnotationsFqName.InjectAnnotation)

  val resolvePredicate: DeclarationPredicate
    get() = has(ProofAnnotationsFqName.ResolveAnnotation)

  val FirDeclaration.idSignature: IdSignature
    get() = checkNotNull(composer.composeSignature(this))

  val resolve: FirSimpleFunction
    get() =
      session
        .symbolProvider
        .getTopLevelCallableSymbols(FqName("arrow.inject.annotations"), Name.identifier("resolve"))
        .first()
        .fir as
        FirSimpleFunction

  val FirAnnotation.isContextAnnotation: Boolean
    get() {
      val annotations: List<FirAnnotation> =
        typeRef.toClassLikeSymbol(session)?.annotations.orEmpty()
      return annotations.any { it.fqName(session) == ProofAnnotationsFqName.ContextAnnotation }
    }

  val FirAnnotationContainer.metaContextAnnotations: List<FirAnnotation>
    get() =
      annotations.filter { firAnnotation ->
        firAnnotation.typeRef.toClassLikeSymbol(session)?.annotations.orEmpty().any {
          it.fqName(session) == ProofAnnotationsFqName.ContextAnnotation
        }
      }

  val FirAnnotationContainer.hasMetaContextAnnotation: Boolean
    get() = metaContextAnnotations.isNotEmpty()
}
