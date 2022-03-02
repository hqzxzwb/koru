package com.futuremind.koruksp.processor

import com.futuremind.koruksp.FlowWrapper
import com.futuremind.koruksp.ScopeProvider
import com.futuremind.koruksp.SuspendWrapper
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.KotlinPoetKspPreview
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName


class WrapperClassBuilder(
  originalTypeName: ClassName,
  private val originalTypeSpec: KSClassDeclaration,
  generatedInterfaces: Map<TypeName, GeneratedInterface>,
  private val newTypeName: String,
  private val scopeProviderMemberName: MemberName?,
  private val freezeWrapper: Boolean
) {

    companion object {
        private const val SCOPE_PROVIDER_PROPERTY_NAME = "scopeProvider"
    }

    private val scopeProviderPropertySpec = PropertySpec
        .builder(
            SCOPE_PROVIDER_PROPERTY_NAME,
            ScopeProvider::class.asTypeName().copy(nullable = true)
        )
        .initializer(
            buildCodeBlock {
                when (scopeProviderMemberName) {
                    null -> add("null")
                    else -> add("%M", scopeProviderMemberName)
                }
            }
        )
        .addModifiers(KModifier.PRIVATE)
        .build()

    @OptIn(KotlinPoetKspPreview::class)
    private val functions = originalTypeSpec.getAllFunctions()
        .filter { !it.modifiers.contains(Modifier.PRIVATE) }
        .mapNotNullTo(arrayListOf()) { originalFuncSpec ->
            FunSpec.builder(name = originalFuncSpec.simpleName.asString() + "Native")
                .addParameters(originalFuncSpec.parameters.map { ParameterSpec(it.name!!.asString(), it.type.toTypeName()) })
                .receiver(originalTypeSpec.toClassName())
                .clearBody()
                .setFunctionBody(originalFuncSpec)
                ?.setReturnType(originalFuncSpec)
                ?.build()
        }

    @OptIn(KotlinPoetKspPreview::class)
    private val properties = originalTypeSpec.getAllProperties()
        .filter { !it.modifiers.contains(Modifier.PRIVATE) }
        .mapNotNullTo(arrayListOf()) { originalPropertySpec ->
            if (!originalPropertySpec.type.toTypeName().isFlow) {
                return@mapNotNullTo null
            }
            PropertySpec
                .builder(
                    name = originalPropertySpec.simpleName.asString() + "Native",
                    type = originalPropertySpec.type.toTypeName().wrappedType
                )
                .receiver(originalTypeSpec.toClassName())
                .getter(
                    FunSpec.getterBuilder()
                        .setGetterBody(originalPropertySpec)
                        .build()
                )
                .mutable(false)
                .apply { modifiers.remove(KModifier.ABSTRACT) }
                .build()
        }

    //this could be simplified in the future, but for now: https://github.com/square/kotlinpoet/issues/966
    @OptIn(KotlinPoetKspPreview::class)
    private fun FunSpec.Builder.setFunctionBody(originalFunSpec: KSFunctionDeclaration): FunSpec.Builder? = when {
        originalFunSpec.modifiers.contains(Modifier.SUSPEND) -> wrapOriginalSuspendFunction(originalFunSpec)
        originalFunSpec.returnType?.toTypeName().isFlow -> wrapOriginalFlowFunction(originalFunSpec)
        else -> null
    }

    @OptIn(KotlinPoetKspPreview::class)
    private fun FunSpec.Builder.setGetterBody(originalPropSpec: KSPropertyDeclaration): FunSpec.Builder {
        val getterInvocation = when {
            originalPropSpec.type.toTypeName().isFlow -> flowWrapperFunctionBody(originalPropSpec.asInvocation()).toString()
            else -> "return ${originalPropSpec.asInvocation()}"
        }
        return this.addStatement(getterInvocation)
    }

    /** E.g. return SuspendWrapper(mainScopeProvider) { doSth(whatever) }*/
    private fun FunSpec.Builder.wrapOriginalSuspendFunction(
        originalFunSpec: KSFunctionDeclaration
    ): FunSpec.Builder = addCode(
        buildCodeBlock {
            add("return ${SuspendWrapper::class.qualifiedName}(")
            add(SCOPE_PROVIDER_PROPERTY_NAME)
            add(", ")
            add("%L", freezeWrapper)
            add(") ")
            add("{ ${originalFunSpec.asInvocation()} }")
        }
    )

    /** E.g. return FlowWrapper(mainScopeProvider, doSth(whatever)) */
    private fun FunSpec.Builder.wrapOriginalFlowFunction(
        originalFunSpec: KSFunctionDeclaration
    ): FunSpec.Builder = addCode(
        flowWrapperFunctionBody(originalFunSpec.asInvocation())
    )

    private fun flowWrapperFunctionBody(callOriginal: String) = buildCodeBlock {
        add("return %T(", FlowWrapper::class)
        add(SCOPE_PROVIDER_PROPERTY_NAME)
        add(", %L", freezeWrapper)
        add(", ${callOriginal})")
    }

    private fun FunSpec.Builder.callOriginalBlockingFunction(originalFunSpec: KSFunctionDeclaration): FunSpec.Builder =
        this.addStatement("return ${originalFunSpec.asInvocation()}")

    private fun KSFunctionDeclaration.asInvocation(): String {
        val paramsDeclaration = parameters.joinToString(", ") { it.name?.asString().orEmpty() }
        return "this.${this.simpleName.asString()}($paramsDeclaration)"
    }

    private fun KSPropertyDeclaration.asInvocation(): String {
        return "this.${this.simpleName.asString()}"
    }

    fun build(): FileSpec = FileSpec
        .builder(originalTypeSpec.packageName.asString(), newTypeName)
        .addProperty(scopeProviderPropertySpec)
        .also { properties.forEach(it::addProperty) }
        .also { functions.forEach(it::addFunction) }
        .build()

}
