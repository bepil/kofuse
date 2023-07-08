package com.github.bepil.kofuse.util

import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.idea.base.utils.fqname.fqName
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * Returns the fully qualified return type of this function.
 */
val KtNamedFunction.returnTypeFqName
    get() = (this.resolveToDescriptorIfAny() as? CallableDescriptor)?.returnTypeFqName

private val CallableDescriptor.returnTypeFqName
    get() = returnType?.fqName?.asString()
