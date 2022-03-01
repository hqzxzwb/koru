package com.futuremind.koru.processor

import com.futuremind.koru.FlowWrapper
import com.futuremind.koru.ScopeProvider
import com.futuremind.koru.SuspendWrapper
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.KotlinPoetKspPreview
import com.squareup.kotlinpoet.ksp.toTypeName


class WrapperClassBuilder(
    originalTypeName: ClassName,
    originalTypeSpec: KSClassDeclaration,
    generatedInterfaces: Map<TypeName, GeneratedInterface>,
    private val newTypeName: String,
    private val scopeProviderMemberName: MemberName?,
    private val freezeWrapper: Boolean
) : WrapperBuilder(originalTypeName, originalTypeSpec, generatedInterfaces) {

    companion object {
        private const val WRAPPED_PROPERTY_NAME = "wrapped"
        private const val SCOPE_PROVIDER_PROPERTY_NAME = "scopeProvider"
    }

    private val constructorSpec = FunSpec
        .constructorBuilder()
        .addParameter(WRAPPED_PROPERTY_NAME, originalTypeName)
        .addParameter(
            ParameterSpec
                .builder(
                    SCOPE_PROVIDER_PROPERTY_NAME,
                    ScopeProvider::class.asTypeName().copy(nullable = true)
                )
                .build()
        )
        .apply {
            if (freezeWrapper) {
                this.addStatement(
                    "this.%M()",
                    MemberName("com.futuremind.koru", "freeze")
                )
            }
        }
        .build()

    private val secondaryConstructorSpec = FunSpec
        .constructorBuilder()
        .addParameter(WRAPPED_PROPERTY_NAME, originalTypeName)
        .callThisConstructor(
            buildCodeBlock {
                add("%N", WRAPPED_PROPERTY_NAME)
                add(",")
                when (scopeProviderMemberName) {
                    null -> add("null")
                    else -> add("%M", scopeProviderMemberName)
                }
            }
        )
        .build()

    private val wrappedClassPropertySpec = PropertySpec
        .builder(WRAPPED_PROPERTY_NAME, originalTypeName)
        .initializer(WRAPPED_PROPERTY_NAME)
        .addModifiers(KModifier.PRIVATE)
        .build()

    private val scopeProviderPropertySpec = PropertySpec
        .builder(
            SCOPE_PROVIDER_PROPERTY_NAME,
            ScopeProvider::class.asTypeName().copy(nullable = true)
        )
        .initializer(SCOPE_PROVIDER_PROPERTY_NAME)
        .addModifiers(KModifier.PRIVATE)
        .build()

    private val functions = originalTypeSpec.getAllFunctions()
        .filter { !it.modifiers.contains(Modifier.PRIVATE) }
        .mapNotNullTo(arrayListOf()) { originalFuncSpec ->
            FunSpec.builder(name = originalFuncSpec.simpleName.asString())
                .clearBody()
                .setFunctionBody(originalFuncSpec)
                ?.setReturnType(originalFuncSpec)
                ?.apply {
                    modifiers.remove(KModifier.SUSPEND)
                    modifiers.remove(KModifier.ABSTRACT)
                }
                ?.build()
        }

    @OptIn(KotlinPoetKspPreview::class)
    private val properties = originalTypeSpec.getAllProperties()
        .filter { !it.modifiers.contains(Modifier.PRIVATE) }
        .mapNotNullTo(arrayListOf()) { originalPropertySpec ->
            PropertySpec
                .builder(
                    name = originalPropertySpec.simpleName.asString(),
                    type = originalPropertySpec.type.toTypeName().wrappedType
                )
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
            add("return %T(", SuspendWrapper::class)
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
        return "${WRAPPED_PROPERTY_NAME}.${this.simpleName.asString()}($paramsDeclaration)"
    }

    private fun KSPropertyDeclaration.asInvocation(): String {
        return "${WRAPPED_PROPERTY_NAME}.${this.simpleName.asString()}"
    }

    fun build(): TypeSpec = TypeSpec
        .classBuilder(newTypeName)
        .addModifiers(modifiers)
        .addSuperinterfaces(superInterfacesNames)
        .primaryConstructor(constructorSpec)
        .addFunction(secondaryConstructorSpec)
        .addProperty(wrappedClassPropertySpec)
        .addProperty(scopeProviderPropertySpec)
        .addProperties(properties)
        .addFunctions(functions)
        .build()

}
