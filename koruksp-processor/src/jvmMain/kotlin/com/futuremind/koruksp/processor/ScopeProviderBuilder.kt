package com.futuremind.koruksp.processor

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import java.util.Locale
import com.futuremind.koruksp.ScopeProvider
import com.google.devtools.ksp.symbol.KSClassDeclaration


/**
 * Generates a top level property [ScopeProvider] exposing a CoroutineScope to be injected into
 * generated native classes via @ToNativeClass(launchOnScope = ...).
 */
class ScopeProviderBuilder(
    packageName: String,
    poetMetadataSpec: KSClassDeclaration
) {

    private val scopeProviderClassName = ClassName(packageName, poetMetadataSpec.simpleName.asString())
    private val scopePropertyName = "exportedScopeProvider_"+ poetMetadataSpec.simpleName.asString()
        .replaceFirstChar { it.lowercase(Locale.ROOT) }

    fun build(): PropertySpec = PropertySpec
        .builder(scopePropertyName, scopeProviderClassName, KModifier.PUBLIC)
        .initializer("%T()", scopeProviderClassName)
        .build()

}
