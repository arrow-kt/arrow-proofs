package arrow.meta.plugins.analysis.phases.analysis.solver.collect

import arrow.meta.plugins.analysis.phases.analysis.solver.SpecialKind
import arrow.meta.plugins.analysis.phases.analysis.solver.allArgumentExpressions
import arrow.meta.plugins.analysis.phases.analysis.solver.arg
import arrow.meta.plugins.analysis.phases.analysis.solver.ast.context.elements.FqName
import arrow.meta.plugins.analysis.phases.analysis.solver.check.RESULT_VAR_NAME
import arrow.meta.plugins.analysis.phases.analysis.solver.collect.model.DeclarationConstraints
import arrow.meta.plugins.analysis.phases.analysis.solver.collect.model.NamedConstraint
import arrow.meta.plugins.analysis.phases.analysis.solver.errors.ErrorMessages
import arrow.meta.plugins.analysis.phases.analysis.solver.primitiveFormula
import arrow.meta.plugins.analysis.phases.analysis.solver.state.SolverState
import arrow.meta.plugins.analysis.smt.ObjectFormula
import arrow.meta.plugins.analysis.smt.Solver
import arrow.meta.plugins.analysis.smt.boolAnd
import arrow.meta.plugins.analysis.smt.boolAndList
import arrow.meta.plugins.analysis.smt.boolOr
import arrow.meta.plugins.analysis.smt.boolOrList
import arrow.meta.plugins.analysis.smt.isFieldCall
import arrow.meta.plugins.analysis.smt.isSingleVariable
import arrow.meta.plugins.analysis.types.PrimitiveType
import arrow.meta.plugins.analysis.types.asFloatingLiteral
import arrow.meta.plugins.analysis.types.asIntegerLiteral
import arrow.meta.plugins.analysis.types.primitiveType
import arrow.meta.plugins.analysis.types.unwrapIfNullable
import arrow.meta.plugins.analysis.phases.analysis.solver.ast.context.elements.AnnotatedExpression
import arrow.meta.plugins.analysis.phases.analysis.solver.ast.context.elements.BinaryExpression
import arrow.meta.plugins.analysis.phases.analysis.solver.ast.context.elements.BlockExpression
import arrow.meta.plugins.analysis.phases.analysis.solver.ast.context.elements.ConstantExpression
import arrow.meta.plugins.analysis.phases.analysis.solver.ast.context.elements.Constructor
import arrow.meta.plugins.analysis.phases.analysis.solver.ast.context.elements.Declaration
import arrow.meta.plugins.analysis.phases.analysis.solver.ast.context.elements.DeclarationWithBody
import arrow.meta.plugins.analysis.phases.analysis.solver.ast.context.elements.DeclarationWithInitializer
import arrow.meta.plugins.analysis.phases.analysis.solver.ast.context.elements.Element
import arrow.meta.plugins.analysis.phases.analysis.solver.ast.context.elements.Expression
import arrow.meta.plugins.analysis.phases.analysis.solver.ast.context.elements.Function
import arrow.meta.plugins.analysis.phases.analysis.solver.ast.context.elements.IfExpression
import arrow.meta.plugins.analysis.phases.analysis.solver.ast.context.elements.ExpressionLambdaArgument
import arrow.meta.plugins.analysis.phases.analysis.solver.ast.context.elements.LambdaExpression
import arrow.meta.plugins.analysis.phases.analysis.solver.ast.context.elements.NameReferenceExpression
import arrow.meta.plugins.analysis.phases.analysis.solver.ast.context.elements.ParenthesizedExpression
import arrow.meta.plugins.analysis.phases.analysis.solver.ast.context.elements.ReturnExpression
import arrow.meta.plugins.analysis.phases.analysis.solver.ast.context.elements.ThisExpression
import arrow.meta.plugins.analysis.phases.analysis.solver.ast.context.ResolutionContext
import arrow.meta.plugins.analysis.phases.analysis.solver.ast.context.ResolvedCall
import arrow.meta.plugins.analysis.phases.analysis.solver.ast.context.types.Type
import arrow.meta.plugins.analysis.phases.analysis.solver.ast.context.descriptors.AnalysisResult
import arrow.meta.plugins.analysis.phases.analysis.solver.ast.context.descriptors.Annotated
import arrow.meta.plugins.analysis.phases.analysis.solver.ast.context.descriptors.AnnotationDescriptor
import arrow.meta.plugins.analysis.phases.analysis.solver.ast.context.descriptors.CallableDescriptor
import arrow.meta.plugins.analysis.phases.analysis.solver.ast.context.descriptors.ClassDescriptor
import arrow.meta.plugins.analysis.phases.analysis.solver.ast.context.descriptors.ConstructorDescriptor
import arrow.meta.plugins.analysis.phases.analysis.solver.ast.context.descriptors.DeclarationDescriptor
import arrow.meta.plugins.analysis.phases.analysis.solver.ast.context.descriptors.ExpressionValueArgument
import arrow.meta.plugins.analysis.phases.analysis.solver.ast.context.descriptors.FunctionDescriptor
import arrow.meta.plugins.analysis.phases.analysis.solver.ast.context.descriptors.LocalVariableDescriptor
import arrow.meta.plugins.analysis.phases.analysis.solver.ast.context.descriptors.ModuleDescriptor
import arrow.meta.plugins.analysis.phases.analysis.solver.ast.context.descriptors.PackageViewDescriptor
import arrow.meta.plugins.analysis.phases.analysis.solver.ast.context.descriptors.ParameterDescriptor
import arrow.meta.plugins.analysis.phases.analysis.solver.ast.context.descriptors.PropertyDescriptor
import arrow.meta.plugins.analysis.phases.analysis.solver.ast.context.descriptors.ReceiverParameterDescriptor
import arrow.meta.plugins.analysis.phases.analysis.solver.ast.context.descriptors.TypeAliasDescriptor
import arrow.meta.plugins.analysis.phases.analysis.solver.ast.context.descriptors.ValueParameterDescriptor
import arrow.meta.plugins.analysis.phases.analysis.solver.ast.context.descriptors.withAliasUnwrapped
import arrow.meta.plugins.analysis.phases.analysis.solver.ast.context.elements.CallExpression
import arrow.meta.plugins.analysis.phases.analysis.solver.ast.context.elements.DotQualifiedExpression
import arrow.meta.plugins.analysis.phases.analysis.solver.ast.context.elements.ExpressionResolvedValueArgument
import arrow.meta.plugins.analysis.phases.analysis.solver.ast.context.elements.NullExpression
import arrow.meta.plugins.analysis.phases.analysis.solver.ast.context.elements.Parameter
import arrow.meta.plugins.analysis.phases.analysis.solver.ast.context.elements.WhenConditionWithExpression
import arrow.meta.plugins.analysis.phases.analysis.solver.ast.context.elements.WhenEntry
import arrow.meta.plugins.analysis.phases.analysis.solver.ast.context.elements.WhenExpression
import arrow.meta.plugins.analysis.phases.analysis.solver.isRequireCall
import arrow.meta.plugins.analysis.phases.analysis.solver.resolvedArg
import arrow.meta.plugins.analysis.phases.analysis.solver.specialKind
import org.sosy_lab.java_smt.api.BooleanFormula
import org.sosy_lab.java_smt.api.Formula
import org.sosy_lab.java_smt.api.FormulaManager
import org.sosy_lab.java_smt.api.FormulaType
import org.sosy_lab.java_smt.api.FunctionDeclaration
import org.sosy_lab.java_smt.api.visitors.FormulaTransformationVisitor

// PHASE 1: COLLECTION OF CONSTRAINTS
// ==================================

/**
 * Collects constraints from all declarations and adds them to the solver state
 */
public fun collectDeclarationsConstraints(
  solverState: SolverState?,
  context: ResolutionContext,
  declaration: Declaration,
  descriptor: DeclarationDescriptor
) {
  if (solverState != null && (solverState.isIn(SolverState.Stage.Init) || solverState.isIn(SolverState.Stage.CollectConstraints))) {
    solverState.collecting()
    declaration.constraints(solverState, context, descriptor)
  }
}

/**
 * Gather constraints from the local module by inspecting
 * - Custom DSL elements (pre and post)
 * - Ad-hoc constraints over third party types TODO
 * - Annotated declarations in compiled third party dependency modules TODO
 */
internal fun Declaration.constraints(
  solverState: SolverState,
  context: ResolutionContext,
  descriptor: DeclarationDescriptor
) = when (this) {
  is Constructor<*> ->
    constraintsFromConstructor(solverState, context, valueParameters)
  is DeclarationWithBody ->
    constraintsFromFunctionLike(solverState, context, valueParameters.filterNotNull())
  is DeclarationWithInitializer ->
    constraintsFromFunctionLike(solverState, context, emptyList())
  else -> Pair(arrayListOf(), arrayListOf())
}.let { (preConstraints, postConstraints) ->
  if (preConstraints.isNotEmpty() || postConstraints.isNotEmpty() || descriptor.isField()) {
    solverState.addConstraints(
      descriptor, preConstraints, postConstraints,
      context
    )
  }
}

/**
 * Obtain all the function calls and corresponding formulae
 * to 'pre', 'post', and 'require'
 */
private fun Declaration.constraintsFromDeclaration(
  solverState: SolverState,
  context: ResolutionContext,
  parameters: List<Parameter>
): List<Pair<ResolvedCall, NamedConstraint>> = context.run {
  constraintsDSLElements().toList().mapNotNull {
    it.elementToConstraint(solverState, context, parameters)
  }
}

/**
 * Gather constraints for anything which is not a constructor
 */
private fun Declaration.constraintsFromFunctionLike(
  solverState: SolverState,
  context: ResolutionContext,
  parameters: List<Parameter>
): Pair<ArrayList<NamedConstraint>, ArrayList<NamedConstraint>> {
  val preConstraints = arrayListOf<NamedConstraint>()
  val postConstraints = arrayListOf<NamedConstraint>()
  constraintsFromDeclaration(solverState, context, parameters).forEach { (call, formula) ->
    when (call.specialKind) {
      SpecialKind.Pre -> preConstraints.add(formula)
      SpecialKind.Post -> postConstraints.add(formula)
      else -> { } // do nothing
    }
  }
  return Pair(preConstraints, postConstraints)
}

/**
 * Constructors have some additional requirements,
 * namely the pre- and post-conditions of init blocks
 * should be added to their own list
 */
private fun <A : Constructor<A>> Constructor<A>.constraintsFromConstructor(
  solverState: SolverState,
  context: ResolutionContext,
  parameters: List<Parameter>
): Pair<ArrayList<NamedConstraint>, ArrayList<NamedConstraint>> {
  val preConstraints = arrayListOf<NamedConstraint>()
  val postConstraints = arrayListOf<NamedConstraint>()
  (getContainingClassOrObject().getAnonymousInitializers() + listOf(this)).flatMap {
    it.constraintsFromDeclaration(solverState, context, parameters)
  }.forEach { (call, formula) ->
    if (call.specialKind == SpecialKind.Pre) {
      rewritePrecondition(solverState, context, !call.isRequireCall(), call, formula.formula)?.let {
        preConstraints.add(NamedConstraint(formula.msg, it))
      }
    }
    // in constructors 'require' has a double duty
    if (call.specialKind == SpecialKind.Post || call.isRequireCall()) {
      rewritePostcondition(solverState, formula.formula).let {
        postConstraints.add(NamedConstraint(formula.msg, it))
      }
    }
  }
  return Pair(preConstraints, postConstraints)
}

/**
 * Turn references to 'field(x, this)'
 * into references to parameter 'x'
 */
private fun <A : Constructor<A>> Constructor<A>.rewritePrecondition(
  solverState: SolverState,
  context: ResolutionContext,
  raiseErrorWhenUnexpected: Boolean,
  call: ResolvedCall,
  formula: BooleanFormula
): BooleanFormula? {
  val mgr = solverState.solver.formulaManager
  var errorSignaled = false
  val result = mgr.transformRecursively(
    formula,
    object : FormulaTransformationVisitor(mgr) {
      override fun visitFunction(f: Formula?, args: MutableList<Formula>?, fn: FunctionDeclaration<*>?): Formula =
        if (fn?.name == Solver.FIELD_FUNCTION_NAME) {
          val fieldName = args?.getOrNull(0)?.let { mgr.extractSingleVariable(it) }
          val thisName = args?.getOrNull(1)?.let { mgr.extractSingleVariable(it) }
          val paramName = this@rewritePrecondition.valueParameters.firstOrNull { param ->
            fieldName?.endsWith(".${param.nameAsName?.value}") ?: (param.nameAsName?.value == fieldName)
          }?.nameAsName?.value
          if (fieldName != null && thisName == "this") {
            if (paramName != null) {
              solverState.solver.makeObjectVariable(paramName)
            } else {
              // error case, we have a reference to a field
              // which is not coming from an argument
              errorSignaled = true
              if (raiseErrorWhenUnexpected) {
                val msg = ErrorMessages.Parsing.unexpectedFieldInitBlock(fieldName)
                context.reportErrorsParsingPredicate(call.callElement, msg)
              }
              super.visitFunction(f, args, fn)
            }
          } else {
            super.visitFunction(f, args, fn)
          }
        } else {
          super.visitFunction(f, args, fn)
        }
    })
  return result.takeIf { !errorSignaled }
}

/**
 * Turn references to 'this'
 * into references to '$result'
 */
private fun rewritePostcondition(
  solverState: SolverState,
  formula: BooleanFormula
): BooleanFormula {
  val mgr = solverState.solver.formulaManager
  return mgr.transformRecursively(
    formula,
    object : FormulaTransformationVisitor(mgr) {
      override fun visitFreeVariable(f: Formula?, name: String?): Formula =
        if (name == "this") {
          solverState.solver.makeObjectVariable(RESULT_VAR_NAME)
        } else {
          super.visitFreeVariable(f, name)
        }
    })
}

private fun FormulaManager.extractSingleVariable(
  formula: Formula
): String? =
  extractVariables(formula)
    .takeIf { it.size == 1 }
    ?.toList()?.getOrNull(0)?.first

private fun Element.elementToConstraint(
  solverState: SolverState,
  context: ResolutionContext,
  parameters: List<Parameter>
): Pair<ResolvedCall, NamedConstraint>? {
  val call = getResolvedCall(context)
  val kind = call?.specialKind
  return if (kind == SpecialKind.Pre || kind == SpecialKind.Post) {
    val predicateArg = call.arg("predicate", context) ?: call.arg("value", context)
    val result = solverState.solver.expressionToFormula(predicateArg, context, parameters, false) as? BooleanFormula
    if (result == null) {
      val msg = ErrorMessages.Parsing.errorParsingPredicate(predicateArg)
      context.reportErrorsParsingPredicate(this, msg)
      solverState.signalParseErrors()
      null
    } else {
      val msgBody = call.arg("msg", context) ?: call.arg("lazyMessage", context)
      val msg = if (msgBody is LambdaExpression) msgBody.bodyExpression?.firstStatement?.text?.trim('"')
      else msgBody?.text ?: predicateArg?.text
      msg?.let { call to NamedConstraint(it, result) }
    }
  } else {
    null
  }
}

/**
 * returns true if we have declared something with a @Law
 */
fun DeclarationDescriptor.isALaw(): Boolean =
  annotations().hasAnnotation(FqName("arrow.analysis.Law")) ||
    containingDeclaration.isLawsType()

fun DeclarationDescriptor?.isLawsType(): Boolean = when (this) {
  is ClassDescriptor -> superTypes.any {
    it.descriptor?.fqNameSafe == FqName("arrow.analysis.Laws")
  }
  else -> false
}

/**
 * Depending on the source of the [descriptor] we might
 * need to attach the information to different places.
 * The main case is when you declare a @Law, in which
 * case you have to look for the proper place.
 */
private fun SolverState.addConstraints(
  descriptor: DeclarationDescriptor,
  preConstraints: ArrayList<NamedConstraint>,
  postConstraints: ArrayList<NamedConstraint>,
  bindingContext: ResolutionContext
) {
  val lawSubject =
    findDescriptorFromRemoteLaw(descriptor) ?: findDescriptorFromLocalLaw(descriptor, bindingContext)
  if (lawSubject is CallableDescriptor && lawSubject.fqNameSafe == FqName("arrow.analysis.post"))
    throw Exception("trying to attach to post, this is wrong!")
  if (lawSubject != null) {
    val renamed = solver.renameConditions(
      DeclarationConstraints(descriptor, preConstraints, postConstraints),
      lawSubject.withAliasUnwrapped)
    callableConstraints.add(renamed.descriptor, ArrayList(renamed.pre), ArrayList(renamed.post))
  }
  callableConstraints.add(descriptor, preConstraints, postConstraints)
}

private fun findDescriptorFromRemoteLaw(
  descriptor: DeclarationDescriptor
): DeclarationDescriptor? =
  descriptor.annotations().findAnnotation(FqName("arrow.analysis.Subject"))?.let { lawSubject ->
    val name = lawSubject.argumentValueAsString("fqName")
    val result = name?.let {
      descriptor.module.obtainDeclaration(FqName(it), descriptor)
    }
    result.also {
      if (it == null) {
        if (name == null)
          throw Exception(ErrorMessages.Parsing.subjectWithoutName(descriptor.fqNameSafe.name))
        else
          throw Exception(ErrorMessages.Parsing.couldNotResolveSubject(name, descriptor.fqNameSafe.name))
      }
    }
  }

private fun ModuleDescriptor.obtainDeclaration(
  fqName: FqName,
  compatibleWith: DeclarationDescriptor
): DeclarationDescriptor? {
  val portions = fqName.name.split("/")

  var current: DeclarationDescriptor? = getPackage(portions.first())
  for (portion in portions.drop(1)) {
    val elts = when (portion) {
      "<init>" -> when (current) {
        is ClassDescriptor -> current.constructors
        is TypeAliasDescriptor -> current.constructors
        else -> null
      }
      else -> {
        when (current) {
          is PackageViewDescriptor -> current.getMemberScope()
          is ClassDescriptor -> current.getUnsubstitutedMemberScope()
          is TypeAliasDescriptor -> current.classDescriptor?.getUnsubstitutedMemberScope()
          else -> null
        }?.let {
          it.getContributedDescriptors { true }
        }?.filter {
          it.name.value == portion
        }
      }
    }
    current = elts?.firstOrNull {
      it.isCompatibleWith(compatibleWith)
    }
  }

  return current
}

fun DeclarationDescriptor.isCompatibleWith(
  other: DeclarationDescriptor
): Boolean = when {
  this is CallableDescriptor && other is CallableDescriptor -> {
    // we have to ignore the parameters which come from Laws
    val params1 = this.allParameters.filter { param ->
      !param.type.descriptor.isLawsType() &&
        !(this is ConstructorDescriptor && param is ReceiverParameterDescriptor)
    }
    val params2 = other.allParameters.filter { param ->
      !param.type.descriptor.isLawsType() &&
        !(other is ConstructorDescriptor && param is ReceiverParameterDescriptor)
    }
    params1.size == params2.size &&
      params1.zip(params2).all { (p1, p2) ->
        p1.type.isEqualTo(p2.type)
      }
  }
  else -> true
}

private fun SolverState.findDescriptorFromLocalLaw(
  descriptor: DeclarationDescriptor,
  bindingContext: ResolutionContext
): DeclarationDescriptor? {
  if (!descriptor.isALaw())
    return null

  if (descriptor !is CallableDescriptor)
    return null

  val lawCall =
    (descriptor.element() as? Function)
      ?.let { getReturnedExpressionWithoutPostcondition(it, bindingContext) }
  if (lawCall == null) {
    descriptor.element()?.let { elt ->
      val msg = ErrorMessages.Parsing.lawMustCallFunction()
      bindingContext.reportErrorsParsingPredicate(elt, msg)
      signalParseErrors()
    }
    return null
  }

  val parameters = descriptor.allParameters.filter { !it.type.descriptor.isLawsType() }
  val arguments = lawCall.allArgumentExpressions(bindingContext).map { it.expression }
  val check = parameters.zip(arguments).all { (param, arg) ->
    (param is ReceiverParameterDescriptor && (arg == null || arg is ThisExpression)) ||
      (param is ValueParameterDescriptor && arg is NameReferenceExpression && arg.getReferencedNameAsName() == param.name)
  }
  if (!check) {
    descriptor.element()?.let { elt ->
      val msg = ErrorMessages.Parsing.lawMustHaveParametersInOrder()
      bindingContext.reportErrorsParsingPredicate(elt, msg)
      signalParseErrors()
    }
    return null
  }

  return lawCall.resultingDescriptor
}

private fun MutableMap<FqName, MutableList<DeclarationConstraints>>.add(
  descriptor: DeclarationDescriptor,
  pre: ArrayList<NamedConstraint>,
  post: ArrayList<NamedConstraint>
) {
  val fqName = descriptor.fqNameSafe
  // create a new one if not existent
  if (!containsKey(fqName)) this[fqName] = mutableListOf()
  val list = this[fqName]!!
  // see if there's any compatible
  when (val ix = list.indexOfFirst { it.descriptor.isCompatibleWith(descriptor) }) {
    -1 -> list.add(DeclarationConstraints(descriptor, pre, post))
    else -> {
      val previous = list[ix]
      list[ix] = DeclarationConstraints(descriptor, previous.pre + pre, previous.post + post)
    }
  }
}

private fun getReturnedExpressionWithoutPostcondition(
  function: Function,
  bindingContext: ResolutionContext
): ResolvedCall? {
  val lastElement = function.body()?.lastBlockStatementOrThis()
  val lastElementWithoutReturn = when (lastElement) {
    is ReturnExpression -> lastElement.returnedExpression
    else -> lastElement
  }
  // remove outer layer of postcondition
  val veryLast = when (lastElementWithoutReturn) {
    is DotQualifiedExpression -> {
      val selector = lastElementWithoutReturn.selectorExpression
      val callee = (selector as? CallExpression)?.calleeExpression
      if (callee != null && callee.text == "post") {
        lastElementWithoutReturn.receiverExpression
      } else {
        null
      }
    }
    else -> null
  } ?: lastElementWithoutReturn

  return veryLast?.getResolvedCall(bindingContext)
}
//  ((descriptor.findPsi() as? KtFunction)?.body()
//    ?.lastBlockStatementOrThis() as? KtReturnExpression)?.returnedExpression?.getResolvedCall(bindingContext)?.resultingDescriptor

private fun Annotated.preAnnotation(): AnnotationDescriptor? =
  annotations().findAnnotation(FqName("arrow.analysis.Pre"))

private fun Annotated.postAnnotation(): AnnotationDescriptor? =
  annotations().findAnnotation(FqName("arrow.analysis.Post"))

private val skipPackages = setOf(
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

/**
 * Get all the pre- and post- conditions for declarations
 * in the CLASSPATH, by looking at the annotations.
 */
internal tailrec fun ModuleDescriptor.declarationsWithConstraints(
  acc: List<DeclarationDescriptor> = emptyList(),
  packages: List<FqName> = listOf(FqName("")),
  skipPacks: Set<FqName> = skipPackages
): List<DeclarationDescriptor> =
  when {
    packages.isEmpty() -> acc
    else -> {
      val current = packages.first()
      val topLevelDescriptors = getPackage(current.name)?.getMemberScope()?.getContributedDescriptors { true }?.toList().orEmpty()
      val memberDescriptors = topLevelDescriptors.filterIsInstance<ClassDescriptor>().flatMap {
        it.getUnsubstitutedMemberScope().getContributedDescriptors { true }.toList()
      }
      val allPackageDescriptors = topLevelDescriptors + memberDescriptors
      val packagedProofs = allPackageDescriptors
        .filter {
          it.preAnnotation() != null || it.postAnnotation() != null || it.isField()
        }
      val remaining = (getSubPackagesOf(current) + packages.drop(1)).filter { it !in skipPacks }
      declarationsWithConstraints(acc + packagedProofs.asSequence(), remaining)
    }
  }

internal fun SolverState.addClassPathConstraintsToSolverState(
  descriptor: DeclarationDescriptor,
  bindingContext: ResolutionContext
) {
  val constraints = descriptor.annotations().iterable().mapNotNull { ann ->
    when (ann.fqName) {
      FqName("arrow.analysis.Pre") -> "pre"
      FqName("arrow.analysis.Post") -> "post"
      else -> null
    }?.let { element -> parseFormula(element, ann, descriptor) }
  }
  if (constraints.isNotEmpty()) {
    val preConstraints = arrayListOf<NamedConstraint>()
    val postConstraints = arrayListOf<NamedConstraint>()
    constraints.forEach { (call, formula) ->
      if (call == "pre") preConstraints.addAll(formula)
      if (call == "post") postConstraints.addAll(formula)
    }
    addConstraints(descriptor, preConstraints, postConstraints, bindingContext)
  }
}

/**
 * Parse constraints from annotations.
 */
private fun SolverState.parseFormula(
  element: String,
  annotation: AnnotationDescriptor,
  descriptor: DeclarationDescriptor
): Pair<String, List<NamedConstraint>> {
  fun getArg(arg: String) = annotation.argumentValueAsArrayOfString(arg)

  val dependencies = getArg("dependencies")
  val formulae = getArg("formulae")
  val messages = getArg("messages")
  return element to messages.zip(formulae).map { (msg, formula) ->
    NamedConstraint(msg, parseFormula(descriptor, formula, dependencies.toList()))
  }
}

/**
 * Parse constraints from annotations.
 */
internal fun SolverState.parseFormula(
  descriptor: DeclarationDescriptor,
  formula: String,
  dependencies: List<String>
): BooleanFormula {
  val VALUE_TYPE = "Int"
  val FIELD_TYPE = "Int"
  // build the parameters environment
  val params = (descriptor as? CallableDescriptor)?.let { function ->
    function.valueParameters.joinToString(separator = "\n") { param ->
      "(declare-fun ${param.name} () $VALUE_TYPE)"
    }
  } ?: ""
  // build the dependencies
  val deps = dependencies.joinToString(separator = "\n") {
    "(declare-fun $it () $FIELD_TYPE)"
  }
  // build the rest of the environment
  val rest = """
    (declare-fun this () $VALUE_TYPE)
    (declare-fun $RESULT_VAR_NAME () $VALUE_TYPE)
  """.trimIndent()
  val fullString = "$params\n$deps\n$rest\n(assert $formula)"
  return solver.parse(fullString)
}

/**
 * Instructs the compiler analysis phase that we have finished collecting constraints
 * and its time to Rewind analysis for phase 2
 * [arrow.meta.plugins.analysis.phases.analysis.solver.check.checkDeclarationConstraints]
 */
public fun finalizeConstraintsCollection(
  solverState: SolverState?,
  module: ModuleDescriptor,
  bindingTrace: ResolutionContext
): AnalysisResult =
  if (solverState != null && solverState.isIn(SolverState.Stage.CollectConstraints)) {
    module.declarationsWithConstraints().forEach {
      solverState.addClassPathConstraintsToSolverState(it, bindingTrace)
    }
    solverState.introduceFieldNamesInSolver()
    // solverState.introduceFieldAxiomsInSolver() // only if we introduce a solver with quantifiers
    solverState.collectionEnds()
    if (solverState.hadParseErrors()) {
      AnalysisResult.ParsingError
    } else {
      AnalysisResult.Retry
    }
  } else AnalysisResult.Completed

/**
 * Transform a [Expression] into a [Formula]
 */
internal fun Solver.expressionToFormula(
  ex: Expression?,
  context: ResolutionContext,
  parameters: List<Parameter>,
  allowAnyReference: Boolean
): Formula? {
  val argCall = ex?.getResolvedCall(context)
  val recur = { v: Expression? -> expressionToFormula(v, context, parameters, allowAnyReference) }
  return when {
    // just recur
    ex is ParenthesizedExpression -> recur(ex.expression)
    ex is AnnotatedExpression -> recur(ex.baseExpression)
    ex is LambdaExpression -> recur(ex.bodyExpression)
    // basic blocks
    ex is BlockExpression ->
      ex.statements.mapNotNull { recur(it) as? BooleanFormula }
        .let { conditions -> boolAndList(conditions) }
    ex is ConstantExpression ->
      ex.type(context)?.let { ty -> makeConstant(ty, ex) }
    ex is ThisExpression -> // reference to this
      makeObjectVariable("this")
    ex is NameReferenceExpression && ex.isResultReference(context) ->
      makeObjectVariable(RESULT_VAR_NAME)
    ex is NameReferenceExpression && argCall?.resultingDescriptor is ParameterDescriptor ->
      if (allowAnyReference || parameters.any { it.nameAsName == ex.getReferencedNameAsName() }) {
        makeObjectVariable(ex.getReferencedName())
      } else {
        val msg = ErrorMessages.Parsing.unexpectedReference(ex.getReferencedName())
        context.reportErrorsParsingPredicate(ex, msg)
        null
      }
    ex is NameReferenceExpression && argCall?.resultingDescriptor is LocalVariableDescriptor ->
      if (allowAnyReference) {
        makeObjectVariable(ex.getReferencedName())
      } else {
        val msg = ErrorMessages.Parsing.unexpectedReference(ex.getReferencedName())
        context.reportErrorsParsingPredicate(ex, msg)
        null
      }
    ex is IfExpression -> {
      val cond = recur(ex.condition) as? BooleanFormula
      val thenBranch = recur(ex.thenExpression)
      val elseBranch = recur(ex.elseExpression)
      if (cond != null && thenBranch != null && elseBranch != null) {
        ifThenElse(cond, thenBranch, elseBranch)
      } else {
        null
      }
    }
    ex is WhenExpression && ex.subjectExpression == null ->
      ex.entries.foldRight<WhenEntry, Formula?>(null) { entry, acc ->
        val conditions: List<BooleanFormula?> = when {
          entry.isElse -> listOf(booleanFormulaManager.makeTrue())
          else -> entry.conditions.map { cond ->
            when (cond) {
              is WhenConditionWithExpression -> recur(cond.expression) as? BooleanFormula
              else -> null
            }
          }
        }
        val body = recur(entry.expression)
        when {
          body == null || conditions.any { it == null } ->
            return@foldRight null // error case
          acc != null -> ifThenElse(boolOrList(conditions.filterNotNull()), body, acc)
          entry.isElse -> body
          else -> null
        }
      }
    // special cases which do not always resolve well
    ex is BinaryExpression && ex.operationTokenRpr == "EQEQ" && ex.right is NullExpression ->
      ex.left?.let { recur(it) as? ObjectFormula }?.let { isNull(it) }
    ex is BinaryExpression && ex.operationTokenRpr == "EXCLEQ" && ex.right is NullExpression ->
      ex.left?.let { recur(it) as? ObjectFormula }?.let { isNotNull(it) }
    ex is BinaryExpression && ex.operationTokenRpr == "ANDAND" ->
      recur(ex.left)?.let { leftFormula ->
        recur(ex.right)?.let { rightFormula ->
          boolAnd(listOf(leftFormula, rightFormula))
        }
      }
    ex is BinaryExpression && ex.operationTokenRpr == "OROR" ->
      recur(ex.left)?.let { leftFormula ->
        recur(ex.right)?.let { rightFormula ->
          boolOr(listOf(leftFormula, rightFormula))
        }
      }
    // fall-through case
    argCall != null -> {
      val args = argCall.allArgumentExpressions(context).map { (_, ty, e) ->
        Pair(ty, recur(e))
      }
      val wrappedArgs =
        args.takeIf { args.all { it.second != null } }
          ?.map { (ty, e) -> wrap(e!!, ty) }
      wrappedArgs?.let { primitiveFormula(context, argCall, it) }
        ?: fieldFormula(argCall.resultingDescriptor, args)
    }
    else -> null
  }
}

private fun Solver.wrap(
  formula: Formula,
  type: Type
): Formula = when {
  // only wrap variables and 'field(name, thing)'
  !formulaManager.isSingleVariable(formula) && !isFieldCall(formula) -> formula
  formula is ObjectFormula -> {
    val unwrapped = if (type.isMarkedNullable) type.unwrappedNotNullableType else type
    when (unwrapped.primitiveType()) {
      PrimitiveType.INTEGRAL -> intValue(formula)
      PrimitiveType.RATIONAL -> decimalValue(formula)
      PrimitiveType.BOOLEAN -> boolValue(formula)
      else -> formula
    }
  }
  else -> formula
}

private fun Solver.fieldFormula(
  descriptor: CallableDescriptor,
  args: List<Pair<Type, Formula?>>
): ObjectFormula? = descriptor.takeIf { it.isField() }?.let {
    // create a field, the 'this' may be missing
    val thisExpression =
      (args.getOrNull(0)?.second as? ObjectFormula) ?: makeObjectVariable("this")
    field(descriptor.fqNameSafe.name, thisExpression)
  }

/**
 * Turns a named constant expression into a smt [Formula]
 * represented as a constant declared in the correct theory
 * given this [type].
 *
 * For example if [type] refers to [Int] the constant smt value will have as
 * formula type [FormulaType.IntegerType]
 */
private fun Solver.makeConstant(
  type: Type,
  ex: ConstantExpression
): Formula? = when (type.unwrapIfNullable().primitiveType()) {
  PrimitiveType.INTEGRAL ->
    ex.text.asIntegerLiteral()?.let { integerFormulaManager.makeNumber(it) }
  PrimitiveType.RATIONAL ->
    ex.text.asFloatingLiteral()?.let { rationalFormulaManager.makeNumber(it) }
  PrimitiveType.BOOLEAN ->
    booleanFormulaManager.makeBoolean(ex.text.toBooleanStrict())
  else -> null
}

/**
 * Should we treat a node as a field and create 'field(name, x)'?
 */
internal fun DeclarationDescriptor.isField(): Boolean = when (this) {
  is PropertyDescriptor -> hasOneReceiver()
  is FunctionDescriptor -> valueParameters.isEmpty() && hasOneReceiver()
  else -> false
}

private fun CallableDescriptor.hasOneReceiver(): Boolean =
  (extensionReceiverParameter != null && dispatchReceiverParameter == null) ||
    (extensionReceiverParameter == null && dispatchReceiverParameter != null)

internal fun Element.isResultReference(bindingContext: ResolutionContext): Boolean =
  getPostOrInvariantParent(bindingContext)?.let { parent ->
    val expArg = parent.resolvedArg("predicate") as? ExpressionValueArgument
    val lambdaArg =
      (expArg?.valueArgument as? ExpressionLambdaArgument)?.getLambdaExpression()
        ?: (expArg?.valueArgument as? ExpressionResolvedValueArgument)?.argumentExpression as? LambdaExpression
    val params =
      lambdaArg?.functionLiteral?.valueParameters?.map { it.text }.orEmpty() +
        listOf("it")
    this.text in params.distinct()
  } ?: false

internal fun Element.getPostOrInvariantParent(
  bindingContext: ResolutionContext
): ResolvedCall? =
  this.parents().mapNotNull {
    it.getResolvedCall(bindingContext)
  }.firstOrNull { call ->
    val kind = call.specialKind
    kind == SpecialKind.Post || kind == SpecialKind.Invariant
  }