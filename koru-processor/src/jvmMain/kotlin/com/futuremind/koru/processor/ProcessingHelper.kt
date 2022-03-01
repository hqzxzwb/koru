package com.futuremind.koru.processor

import com.squareup.kotlinpoet.*
import com.futuremind.koru.SuspendWrapper
import com.futuremind.koru.FlowWrapper
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.KotlinPoetKspPreview
import com.squareup.kotlinpoet.ksp.toTypeName
import kotlinx.coroutines.flow.*


@OptIn(KotlinPoetKspPreview::class)
fun FunSpec.Builder.setReturnType(originalFunSpec: KSFunctionDeclaration): FunSpec.Builder = when {
    originalFunSpec.modifiers.contains(Modifier.SUSPEND) -> this.returns(
        SuspendWrapper::class.asTypeName().parameterizedBy(originalFunSpec.returnType?.toTypeName().orUnit)
    )
    originalFunSpec.returnType?.toTypeName().isFlow -> this.returns(
        FlowWrapper::class.asTypeName().parameterizedBy(originalFunSpec.returnType?.toTypeName().flowGenericType)
    )
    else -> this.returns(originalFunSpec.returnType?.toTypeName().orUnit)
}

val TypeName.wrappedType get() = when {
    isFlow -> FlowWrapper::class.asTypeName().parameterizedBy(flowGenericType)
    else -> this
}

val TypeName?.isFlow: Boolean
    get() {
        val rawType = (this as? ParameterizedTypeName)?.rawType?.topLevelClassName()
        return rawType == Flow::class.asTypeName()
            || rawType == StateFlow::class.asTypeName()
            || rawType == MutableStateFlow::class.asTypeName()
            || rawType == SharedFlow::class.asTypeName()
            || rawType == MutableSharedFlow::class.asTypeName()
    }

private val TypeName?.flowGenericType: TypeName
    get() = (this as? ParameterizedTypeName)?.typeArguments?.get(0)
        ?: throw IllegalStateException("Should only be called on Flow TypeName")

val FunSpec.isSuspend: Boolean
    get() = this.modifiers.contains(KModifier.SUSPEND)

private val TypeName?.orUnit
    get() = this ?: Unit::class.asTypeName()