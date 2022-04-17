package arrow.inject.compiler.plugin

import arrow.inject.compiler.plugin.fir.ContextInstanceGenerationExtension
import arrow.inject.compiler.plugin.fir.ProofsAdditionalCheckersExtension
import arrow.inject.compiler.plugin.fir.ProofsDeclarationCheckersExtension
import arrow.inject.compiler.plugin.fir.ProofsResolutionExtension
import arrow.inject.compiler.plugin.fir.ProofsStatusTransformerExtension
import arrow.inject.compiler.plugin.fir.ProofsTypeAttributeExtension
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar
import org.jetbrains.kotlin.fir.visitors.FirTransformer

class ArrowInjectExtensionRegistrar : FirExtensionRegistrar() {

  override fun ExtensionRegistrarContext.configurePlugin() {
    +::ContextInstanceGenerationExtension
    +::ProofsDeclarationCheckersExtension
    +::ProofsResolutionExtension
    +::ProofsTypeAttributeExtension
    +::ProofsStatusTransformerExtension
    +::ProofsAdditionalCheckersExtension
  }
}

// class ArrowInjectComponentRegistrar : ComponentRegistrar {
//  override fun registerProjectComponents(project: MockProject, configuration:
// CompilerConfiguration) {
//    FirExtensionRegistrar.registerExtension(project, ArrowInjectExtensionRegistrar())
////    IrGenerationExtension.registerExtension(project, GeneratedDeclarationsIrBodyFiller())
//  }
// }
