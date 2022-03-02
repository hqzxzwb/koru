package com.futuremind.koruksp.processor

import com.futuremind.koruksp.ExportedScopeProvider
import com.futuremind.koruksp.ScopeProvider
import com.futuremind.koruksp.ToNativeClass
import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.KotlinPoetKspPreview
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo
import com.squareup.kotlinpoet.metadata.KotlinPoetMetadataPreview


@OptIn(KotlinPoetMetadataPreview::class)
class Processor(val environment: SymbolProcessorEnvironment) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {

        val codeGenerator = environment.codeGenerator

        val scopeProviders: Map<ClassName, PropertySpec> = resolver
            .getSymbolsWithAnnotation(ExportedScopeProvider::class.java.canonicalName)
            .map { element ->
                generateScopeProvider(
                    resolver = resolver,
                    element = element as KSDeclaration,
                    targetDir = codeGenerator
                )
            }
            .toMap()

        val generatedInterfaces = mutableMapOf<TypeName, GeneratedInterface>()

        resolver.getSymbolsWithAnnotation(ToNativeClass::class.java.canonicalName)
            .forEach { element ->
                generateWrappedClasses(
                    resolver = resolver,
                    element = element as KSDeclaration,
                    generatedInterfaces = generatedInterfaces,
                    scopeProviders = scopeProviders,
                    targetDir = codeGenerator
                )
            }

        return listOf()
    }

    @OptIn(KotlinPoetKspPreview::class)
    @KotlinPoetMetadataPreview
    private fun generateScopeProvider(
        resolver: Resolver,
        element: KSDeclaration,
        targetDir: CodeGenerator
    ): Pair<ClassName, PropertySpec> {

        val packageName = element.getPackage(resolver)
        val scopeClassSpec = (element as KSClassDeclaration)

        scopeClassSpec.assertExtendsScopeProvider(resolver)

        val originalClassName = element.getClassName(resolver)
        val propertySpec = ScopeProviderBuilder(packageName, scopeClassSpec).build()

        FileSpec
            .builder(originalClassName.packageName, "${originalClassName.simpleName}Container")
            .addProperty(propertySpec)
            .build()
            .writeTo(targetDir, false, listOfNotNull(element.containingFile))

        return originalClassName to propertySpec

    }

    @OptIn(KspExperimental::class, com.squareup.kotlinpoet.ksp.KotlinPoetKspPreview::class)
    @KotlinPoetMetadataPreview
    private fun generateWrappedClasses(
      resolver: Resolver,
      element: KSDeclaration,
      generatedInterfaces: Map<TypeName, GeneratedInterface>,
      scopeProviders: Map<ClassName, PropertySpec>,
      targetDir: CodeGenerator
    ) {

        val originalTypeName = element.getClassName(resolver)
        val typeSpec = (element as KSClassDeclaration)
        val annotation = element.getAnnotationsByType(ToNativeClass::class).first()
        val ksAnnotation = element.annotations.first { it.annotationType.toTypeName().toString() == ToNativeClass::class.java.canonicalName }

        val generatedClassName =
            annotation.name.nonEmptyOr("${originalTypeName.simpleName}Native")

        val classToGenerateSpec = WrapperClassBuilder(
            originalTypeName = originalTypeName,
            originalTypeSpec = typeSpec,
            newTypeName = generatedClassName,
            generatedInterfaces = generatedInterfaces,
            scopeProviderMemberName = obtainScopeProviderMemberName(ksAnnotation, scopeProviders),
            freezeWrapper = annotation.freeze
        ).build()

        classToGenerateSpec
            .writeTo(targetDir, false, listOf(element.containingFile!!))

    }

    @OptIn(KotlinPoetKspPreview::class)
    private fun obtainScopeProviderMemberName(
        annotation: KSAnnotation,
        scopeProviders: Map<ClassName, PropertySpec>
    ): MemberName? {
        //this is the dirtiest hack ever but it works :O
        //there probably is some way of doing this via kotlinpoet-metadata
        //https://area-51.blog/2009/02/13/getting-class-values-from-annotations-in-an-annotationprocessor/
        val annotationValue = annotation.arguments.firstOrNull { it.name?.asString() == "launchOnScope" }?.value
        var typeMirror: KSType? = annotationValue as? KSType
        if (typeMirror != null
            && typeMirror.declaration.qualifiedName?.asString() != "com.futuremind.koruksp.NoScopeProvider" //TODO do not compare strings but types
            && scopeProviders[typeMirror.toTypeName()] == null
        ) {
            throw IllegalStateException("$typeMirror can only be used in @ToNativeClass(launchOnScope) if it has been annotated with @ExportedScopeProvider")
        }
        return scopeProviders[typeMirror?.toTypeName()]?.let {
            MemberName(
                packageName = typeMirror!!.declaration.packageName.asString(),
                simpleName = it.name
            )
        }
    }

    private fun String.nonEmptyOr(or: String) = when (this.isEmpty()) {
        true -> or
        false -> this
    }

    private fun KSClassDeclaration.assertExtendsScopeProvider(resolver: Resolver) {
        val superTypeNames = getAllSuperTypes()
            .mapTo(arrayListOf()) { it.declaration.qualifiedName?.asString() }
        if (!superTypeNames.contains(ScopeProvider::class.asTypeName().canonicalName)) {
            throw IllegalStateException("ExportedScopeProvider can only be applied to a class extending ScopeProvider interface")
        }
    }

}

internal fun KSDeclaration.getPackage(resolver: Resolver) =
    packageName.asString()

internal fun KSDeclaration.getClassName(resolver: Resolver): ClassName {
    return ClassName(this.getPackage(resolver), this.simpleName.asString())
}

data class GeneratedInterface(val name: TypeName, val typeSpec: TypeSpec)

@KspExperimental
@KotlinPoetKspPreview
class ProcessorProvider: SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return Processor(environment)
    }
}
