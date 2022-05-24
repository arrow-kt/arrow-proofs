@file:OptIn(SymbolInternals::class)

package arrow.inject.compiler.plugin.fir.collectors

import arrow.inject.compiler.plugin.fir.FirAbstractProofComponent
import arrow.inject.compiler.plugin.model.Proof
import arrow.inject.compiler.plugin.model.ProofAnnotationsFqName
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.extensions.predicate.metaHas
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.resolve.providers.impl.FirProviderImpl
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.visitors.FirVisitorVoid
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull

internal class LocalProofCollectors(override val session: FirSession) : FirAbstractProofComponent {

  fun collectLocalProofs(): List<Proof> = collectLocalProofsIde() + collectLocalProofsCLI()

  fun collectLocalProofsIde(): List<Proof> =
    session.predicateBasedProvider
      .getSymbolsByPredicate(metaHas(ProofAnnotationsFqName.ContextAnnotation))
      .map { Proof.Implication(it.fir.idSignature, it.fir) }

  private fun collectLocalProofsCLI(): List<Proof> =
    session.firstIsInstanceOrNull<FirProviderImpl>()?.getAllFirFiles().orEmpty().flatMap { file ->
      val localProofs: MutableList<Proof> = mutableListOf()
      file.acceptChildren(
        visitor =
          object : FirVisitorVoid() {
            override fun visitElement(element: FirElement) {
              val declaration = element as? FirDeclaration
              if (declaration != null && declaration.hasMetaContextAnnotation) {
                localProofs.add(Proof.Implication(declaration.idSignature, declaration))
              }
            }
          }
      )
      localProofs
    }

  fun collectLocalInjectables(): Set<CallableId> =
    session.predicateBasedProvider
      .getSymbolsByPredicate(injectPredicate)
      .mapNotNull { (it as? FirCallableSymbol)?.callableId }
      .toSet()
}
