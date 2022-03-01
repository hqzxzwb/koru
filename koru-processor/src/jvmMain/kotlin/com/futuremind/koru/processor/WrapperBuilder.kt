package com.futuremind.koru.processor

import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.KotlinPoetKspPreview
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toKModifier


abstract class WrapperBuilder(
    originalTypeName: ClassName,
    originalTypeSpec: KSClassDeclaration,
    private val generatedInterfaces: Map<TypeName, GeneratedInterface>,
) {

    protected val modifiers: Set<KModifier> = originalTypeSpec.modifiers.mapNotNullTo(mutableSetOf()) { it.toKModifier() }.let {
        if (it.contains(KModifier.PRIVATE)) throw IllegalStateException("Cannot wrap types with `private` modifier. Consider using internal or public.")
        it.ifEmpty { setOf(KModifier.PUBLIC) }
    }

    /**
     * 1. Add generated standalone superinterfaces if they match the original superinterfaces.
     * 2. Also add the interface generated from this class if it exists.
     */
    @OptIn(KotlinPoetKspPreview::class)
    private val superInterfaces: List<GeneratedInterface> = originalTypeSpec.getAllSuperTypes()
        .mapNotNullTo(arrayListOf()) {
            (it.declaration as? KSClassDeclaration)
                ?.takeIf { it.classKind == ClassKind.INTERFACE }
                ?.toClassName()
        }
        .apply { add(originalTypeName) }
        .mapNotNull { interfaceName ->
            when (val matchingSuper = generatedInterfaces[interfaceName]) {
                null -> null
                else -> matchingSuper
            }
        }

    protected val superInterfacesNames = superInterfaces.map { it.name }

    private fun PropertySpec.overridesGeneratedInterface(): Boolean {

        //not comparing types because we're comparing koru-wrapped interface with original
        fun PropertySpec.hasSameSignature(other: PropertySpec) = this.name == other.name

        fun TypeSpec.containsPropertySignature() =
            this.propertySpecs.any { it.hasSameSignature(this@overridesGeneratedInterface) }

        return superInterfaces.any { it.typeSpec.containsPropertySignature() }
    }

}