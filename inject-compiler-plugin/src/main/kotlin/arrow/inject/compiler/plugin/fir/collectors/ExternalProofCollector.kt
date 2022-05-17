@file:OptIn(SymbolInternals::class, SymbolInternals::class)

package arrow.inject.compiler.plugin.fir.collectors

import arrow.inject.annotations.Context
import arrow.inject.annotations.Inject
import arrow.inject.compiler.plugin.fir.FirAbstractProofComponent
import arrow.inject.compiler.plugin.model.Proof
import io.github.classgraph.ClassGraph
import io.github.classgraph.ClassInfo
import io.github.classgraph.ScanResult
import java.lang.reflect.Field
import java.lang.reflect.Method
import org.jetbrains.kotlin.descriptors.runtime.structure.classId
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.resolve.providers.getClassDeclaredPropertySymbols
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirVariableSymbol
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

internal class ExternalProofCollector(
  override val session: FirSession,
) : FirAbstractProofComponent {

  fun collectExternalProofs(): List<Proof> =
    firClasspathResult
      .filter { it.type == ClasspathResult.Type.Provider }
      .flatMap { result ->
        (result.functions + result.classes + result.properties + result.classProperties).map {
          Proof.Implication(it.fir.idSignature, it.fir)
        }
      }

  fun collectExternalInjectables(): List<CallableId> =
    firClasspathResult.flatMap { result ->
      (result.functions + result.classes + result.properties + result.classProperties).mapNotNull {
        (it as? FirCallableSymbol)?.callableId
      }
    }

  private val firClasspathResult: List<FirClasspathResult>
    get() =
      classpathResult.map { result ->
        FirClasspathResult(
          annotation = FqName(result.annotation),
          type = result.type,
          functions = result.functions.flatMap(session::topLevelFunctionSymbolProviders),
          classes = result.classes.mapNotNull(session::classLikeSymbolProviders),
          properties = result.functions.flatMap(session::topLevelPropertySymbolProviders),
          classProperties =
            result.functions.flatMap { session.classDeclaredPropertySymbolProviders(it) }
        )
      }

  companion object {

    private val classpathResult: List<ClasspathResult> =
      ClassGraph()
        .enableAnnotationInfo()
        .enableClassInfo()
        .enableMethodInfo()
        .enableFieldInfo()
        .rejectPackages(*skipPackages.map(FqName::asString).toTypedArray())
        .acceptPackages()
        .scan()
        .use { scanResult ->
          with(scanResult) {
            contextAnnotations.map { contextAnnotationClassInfo ->
              ClasspathResult(
                ClasspathResult.Type.Provider,
                contextAnnotationClassInfo.name,
                functionProviders(contextAnnotationClassInfo),
                classProviders(contextAnnotationClassInfo),
              )
            }

            injectAnnotations.map { injectAnnotationClassInfo ->
              ClasspathResult(
                ClasspathResult.Type.Injection,
                injectAnnotationClassInfo.name,
                functionProviders(injectAnnotationClassInfo),
                classProviders(injectAnnotationClassInfo),
              )
            }
          }
        }

    private val skipPackages: Set<FqName>
      get() =
        setOf(
          FqName("com.apple"),
          FqName("com.oracle"),
          FqName("org.omg"),
          FqName("com.sun"),
          FqName("META-INF"),
          FqName("jdk"),
          FqName("apple"),
          FqName("java"),
          FqName("javax"),
          FqName("kotlin"),
          FqName("sun")
        )
  }
}

private data class FirClasspathResult(
  val annotation: FqName,
  val type: ClasspathResult.Type,
  val functions: List<FirNamedFunctionSymbol>,
  val classes: List<FirClassLikeSymbol<*>>,
  val properties: List<FirVariableSymbol<*>>,
  val classProperties: List<FirVariableSymbol<*>>,
)

private data class ClasspathResult(
  val type: Type,
  val annotation: String,
  val functions: List<Method>,
  val classes: List<Class<*>>,
) {

  enum class Type {
    Provider,
    Injection,
  }
}

private val ScanResult.contextAnnotations: List<ClassInfo>
  get() = allAnnotations.filter { classInfo -> classInfo.hasAnnotation(Context::class.java) }

private val ScanResult.injectAnnotations: List<ClassInfo>
  get() = allAnnotations.filter { classInfo -> classInfo.hasAnnotation(Inject::class.java) }

private fun ScanResult.functionProviders(annotationClassInfo: ClassInfo): List<Method> =
  getClassesWithMethodAnnotation(annotationClassInfo.name).flatMap { classWithContextMethodInfo ->
    classWithContextMethodInfo.methodInfo
      .filter { function -> function.hasAnnotation(annotationClassInfo.name) }
      .mapNotNull { method -> method.loadClassAndGetMethod() }
  }

private fun ScanResult.classProviders(annotationClassInfo: ClassInfo): List<Class<*>> =
  getClassesWithAnnotation(annotationClassInfo.name).map { contextClassInfo ->
    contextClassInfo.loadClass()
  }

private fun ScanResult.propertyProviders(annotationClassInfo: ClassInfo): List<Field> =
  getClassesWithFieldAnnotation(annotationClassInfo.name).flatMap { classWithContextFieldInfo ->
    classWithContextFieldInfo.fieldInfo
      .filter { fieldInfo -> fieldInfo.hasAnnotation(annotationClassInfo.name) }
      .mapNotNull { field -> field.loadClassAndGetField() }
  }

private val Class<*>.callableId: CallableId
  get() = CallableId(classId, Name.identifier(name))

private fun FirSession.topLevelFunctionSymbolProviders(
  method: Method
): List<FirNamedFunctionSymbol> =
  symbolProvider.getTopLevelFunctionSymbols(
    FqName(method.declaringClass.`package`.name),
    Name.identifier(method.namedSanitized)
  )

private fun FirSession.classDeclaredPropertySymbolProviders(
  method: Method
): List<FirVariableSymbol<*>> =
  symbolProvider.getClassDeclaredPropertySymbols(
    method.declaringClass.classId,
    Name.identifier(method.namedSanitized)
  )

private fun FirSession.topLevelPropertySymbolProviders(method: Method): List<FirPropertySymbol> =
  symbolProvider.getTopLevelPropertySymbols(
    FqName(method.declaringClass.`package`.name),
    Name.identifier(method.namedSanitized)
  )

private fun FirSession.classLikeSymbolProviders(clazz: Class<*>): FirClassLikeSymbol<*>? =
  symbolProvider.getClassLikeSymbolByClassId(clazz.classId)

private val Method.namedSanitized: String
  get() =
    if (name.startsWith("get") && name.contains("\$")) {
      name.substringAfter("get").substringBefore("\$").replaceFirstChar(Char::lowercaseChar)
    } else name
