package arrow.inject.compiler.plugin.fir.collectors

import arrow.inject.compiler.plugin.fir.FirAbstractProofComponent
import arrow.inject.compiler.plugin.model.Proof
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.resolve.providers.impl.FirProviderImpl
import org.jetbrains.kotlin.fir.visitors.FirVisitorVoid
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstance

internal class LocalProofCollectors(override val session: FirSession) : FirAbstractProofComponent {

  fun collectLocalProofs(): List<Proof> =
    session.firstIsInstance<FirProviderImpl>().getAllFirFiles().flatMap { firFile ->
      val localProofs: MutableList<Proof> = mutableListOf()
      firFile.acceptChildren(
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
}
