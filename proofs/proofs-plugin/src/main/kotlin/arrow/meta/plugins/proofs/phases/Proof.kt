package arrow.meta.plugins.proofs.phases

import org.jetbrains.kotlin.descriptors.ClassConstructorDescriptor
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.resolve.descriptorUtil.isExtension
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.calls.util.FakeCallableDescriptorForObject
import org.jetbrains.kotlin.types.KotlinType

sealed class Proof(open val to: KotlinType, open val through: DeclarationDescriptor) {

  inline fun <A> fold(given: GivenProof.() -> A): A =
    when (this) {
      is GivenProof -> given(this)
    }

  abstract fun isContextAmbiguous(other: Proof): Boolean
}

sealed class GivenProof(override val to: KotlinType, override val through: DeclarationDescriptor) :
  Proof(to, through) {
  abstract val callableDescriptor: CallableDescriptor
  val contexts: Set<FqName>
    get() = through.contextualAnnotations()
  override fun isContextAmbiguous(other: Proof): Boolean =
    other is GivenProof && contexts == other.contexts
}

data class ClassProof(override val to: KotlinType, override val through: ClassDescriptor) :
  GivenProof(to, through) {
  override val callableDescriptor: CallableDescriptor
    get() =
      through.unsubstitutedPrimaryConstructor ?: TODO("no primary constructor for ${through.name}")
}

data class ObjectProof(override val to: KotlinType, override val through: ClassDescriptor) :
  GivenProof(to, through) {
  override val callableDescriptor: CallableDescriptor
    get() = FakeCallableDescriptorForObject(through)
}

data class CallableMemberProof(
  override val to: KotlinType,
  override val through: CallableMemberDescriptor
) : GivenProof(to, through) {
  override val callableDescriptor: CallableDescriptor
    get() = through
}

fun DeclarationDescriptor.contextualAnnotations(): Set<FqName> =
  annotations.mapNotNull { if (it.isGivenContextProof()) it.fqName else null }.toSet()

fun DeclarationDescriptor.asProof(): Sequence<Proof> =
  when (this) {
    is PropertyDescriptor -> asProof()
    is ClassConstructorDescriptor -> containingDeclaration.asProof()
    is FunctionDescriptor -> asProof()
    is ClassDescriptor -> asProof()
    is FakeCallableDescriptorForObject -> classDescriptor.asProof()
    else -> emptySequence()
  }

fun AnnotationDescriptor.isGivenContextProof(): Boolean =
  type.constructor.declarationDescriptor?.annotations?.hasAnnotation(FqName("arrow.Context")) ==
    true

fun ClassDescriptor.asProof(): Sequence<Proof> =
  annotations.asSequence().mapNotNull {
    when {
      it.isGivenContextProof() -> asGivenProof()
      else -> TODO("asProof: Unsupported proof declaration type: $this")
    }
  }

fun PropertyDescriptor.asProof(): Sequence<Proof> =
  annotations.asSequence().mapNotNull {
    when {
      it.isGivenContextProof() -> if (!isExtension) asGivenProof() else null
      else -> TODO("asProof: Unsupported proof declaration type: $this")
    }
  }

fun FunctionDescriptor.asProof(): Sequence<Proof> =
  annotations.asSequence().mapNotNull {
    when {
      it.isGivenContextProof() -> if (!isExtension) asGivenProof() else null
      else -> TODO("asProof: Unsupported proof declaration type: $this")
    }
  }

internal fun ClassDescriptor.asGivenProof(): GivenProof =
  if (kind == ClassKind.OBJECT) ObjectProof(defaultType, this) else ClassProof(defaultType, this)

internal fun CallableMemberDescriptor.asGivenProof(): GivenProof? =
  returnType?.let { CallableMemberProof(it, this) }

data class ProofsCache(val proofs: List<Proof>)
