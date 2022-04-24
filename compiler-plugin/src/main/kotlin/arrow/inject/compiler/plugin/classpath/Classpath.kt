package arrow.inject.compiler.plugin.classpath

import arrow.inject.annotations.Context
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

class Classpath(private val session: FirSession) {

  val firClasspathProviderResult: List<FirClasspathProviderResult>
    get() =
      classpathProviderResults.map { result ->
        FirClasspathProviderResult(
          annotation = FqName(result.annotation),
          functions = result.functions.flatMap(session::topLevelFunctionSymbolProviders),
          classes = result.classes.mapNotNull(session::classLikeSymbolProviders),
          properties = result.functions.flatMap(session::topLevelPropertySymbolProviders),
          classProperties =
            result.functions.flatMap { session.classDeclaredPropertySymbolProviders(it) }
        )
      }

  private val classpathProviderResults: List<ClasspathProviderResult>
    get() =
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
              ClasspathProviderResult(
                contextAnnotationClassInfo.name,
                functionProviders(contextAnnotationClassInfo),
                classProviders(contextAnnotationClassInfo),
              )
            }
          }
        }

  internal val skipPackages =
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

data class FirClasspathProviderResult(
  val annotation: FqName,
  val functions: List<FirNamedFunctionSymbol>,
  val classes: List<FirClassLikeSymbol<*>>,
  val properties: List<FirVariableSymbol<*>>,
  val classProperties: List<FirVariableSymbol<*>>,
)

data class ClasspathProviderResult(
  val annotation: String,
  val functions: List<Method>,
  val classes: List<Class<*>>,
)

internal val ScanResult.contextAnnotations: List<ClassInfo>
  get() = allAnnotations.filter { classInfo -> classInfo.hasAnnotation(Context::class.java) }

internal fun ScanResult.functionProviders(annotationClassInfo: ClassInfo): List<Method> =
  getClassesWithMethodAnnotation(annotationClassInfo.name).flatMap { classWithContextMethodInfo ->
    classWithContextMethodInfo.methodInfo
      .filter { function -> function.hasAnnotation(annotationClassInfo.name) }
      .mapNotNull { method -> method.loadClassAndGetMethod() }
  }

internal fun ScanResult.classProviders(annotationClassInfo: ClassInfo): List<Class<*>> =
  getClassesWithAnnotation(annotationClassInfo.name).map { contextClassInfo ->
    contextClassInfo.loadClass()
  }

internal fun ScanResult.propertyProviders(annotationClassInfo: ClassInfo): List<Field> =
  getClassesWithFieldAnnotation(annotationClassInfo.name).flatMap { classWithContextFieldInfo ->
    classWithContextFieldInfo.fieldInfo
      .filter { fieldInfo -> fieldInfo.hasAnnotation(annotationClassInfo.name) }
      .mapNotNull { field -> field.loadClassAndGetField() }
  }

internal val Class<*>.callableId: CallableId
  get() = CallableId(classId, Name.identifier(name))

internal fun FirSession.topLevelFunctionSymbolProviders(
  method: Method
): List<FirNamedFunctionSymbol> =
  symbolProvider.getTopLevelFunctionSymbols(
    FqName(method.declaringClass.packageName),
    Name.identifier(method.namedSanitized)
  )

internal fun FirSession.classDeclaredPropertySymbolProviders(
  method: Method
): List<FirVariableSymbol<*>> =
  symbolProvider.getClassDeclaredPropertySymbols(
    method.declaringClass.classId,
    Name.identifier(method.namedSanitized)
  )

internal fun FirSession.topLevelPropertySymbolProviders(method: Method): List<FirPropertySymbol> =
  symbolProvider.getTopLevelPropertySymbols(
    FqName(method.declaringClass.packageName),
    Name.identifier(method.namedSanitized)
  )

internal fun FirSession.classLikeSymbolProviders(clazz: Class<*>): FirClassLikeSymbol<*>? =
  symbolProvider.getClassLikeSymbolByClassId(clazz.classId)

internal val Method.namedSanitized: String
  get() =
    if (name.startsWith("get") && name.contains("\$")) {
      name.substringAfter("get").substringBefore("\$").replaceFirstChar(Char::lowercaseChar)
    } else name
