package arrow.inject.compiler.plugin.fir.collectors

import arrow.inject.annotations.Contextual
import arrow.inject.compiler.plugin.fir.FirAbstractProofComponent
import arrow.inject.compiler.plugin.fir.FirProofIdSignature
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
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirVariableSymbol
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

internal class ExternalProofCollector(
  override val session: FirSession,
) : FirAbstractProofComponent, FirProofIdSignature {

  fun collectExternalProofs(): List<Proof> =
    firClasspathResult.flatMap { result ->
      (result.functions + result.classes + result.properties + result.classProperties).map {
        Proof.Implication(it.fir.idSignature, it.fir)
      }
    }

  private val firClasspathResult: List<FirClasspathResult>
    get() =
      classpathResult.map { result ->
        FirClasspathResult(
          annotation = FqName(result.annotation),
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
            contextualAnnotations.map { contextAnnotationClassInfo ->
              ClasspathResult(
                contextAnnotationClassInfo.name,
                functionProviders(contextAnnotationClassInfo),
                classProviders(contextAnnotationClassInfo),
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
  val functions: List<FirNamedFunctionSymbol>,
  val classes: List<FirClassLikeSymbol<*>>,
  val properties: List<FirVariableSymbol<*>>,
  val classProperties: List<FirVariableSymbol<*>>,
)

private data class ClasspathResult(
  val annotation: String,
  val functions: List<Method>,
  val classes: List<Class<*>>,
)

private val ScanResult.contextualAnnotations: List<ClassInfo>
  get() = allAnnotations.filter { classInfo -> classInfo.hasAnnotation(Contextual::class.java) }

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
