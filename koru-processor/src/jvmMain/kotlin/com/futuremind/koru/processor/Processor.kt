package com.futuremind.koru.processor

import com.futuremind.koru.*
import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.KotlinPoetKspPreview
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.metadata.KotlinPoetMetadataPreview
import java.io.File


const val KAPT_KOTLIN_GENERATED_OPTION_NAME = "kapt.kotlin.generated"

@KspExperimental
@KotlinPoetKspPreview
@OptIn(KotlinPoetMetadataPreview::class)
class Processor(val environment: SymbolProcessorEnvironment) : SymbolProcessor {

    fun getSupportedAnnotationTypes() = mutableSetOf(
        ToNativeClass::class.java.canonicalName,
        ExportedScopeProvider::class.java.canonicalName
    )

    override fun process(resolver: Resolver): List<KSAnnotated> {

        val kaptGeneratedDir = environment.options[KAPT_KOTLIN_GENERATED_OPTION_NAME]
            ?: throw IllegalStateException("Cannot access kaptKotlinGeneratedDir")

        val scopeProviders: Map<ClassName, PropertySpec> = resolver
            .getSymbolsWithAnnotation(ExportedScopeProvider::class.java.canonicalName)
            .map { element ->
                generateScopeProvider(
                    resolver = resolver,
                    element = element as KSDeclaration,
                    targetDir = kaptGeneratedDir
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
                    kaptGeneratedDir = kaptGeneratedDir
                )
            }

        return listOf()
    }

    @KotlinPoetMetadataPreview
    private fun generateScopeProvider(
        resolver: Resolver,
        element: KSDeclaration,
        targetDir: String
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
            .writeTo(File(targetDir))

        return originalClassName to propertySpec

    }

    @KotlinPoetMetadataPreview
    private fun generateWrappedClasses(
        resolver: Resolver,
        element: KSDeclaration,
        generatedInterfaces: Map<TypeName, GeneratedInterface>,
        scopeProviders: Map<ClassName, PropertySpec>,
        kaptGeneratedDir: String
    ) {

        val originalTypeName = element.getClassName(resolver)
        val typeSpec = (element as KSClassDeclaration)
        val annotation = element.getAnnotationsByType(ToNativeClass::class).first()

        val generatedClassName =
            annotation.name.nonEmptyOr("${originalTypeName.simpleName}Native")

        val classToGenerateSpec = WrapperClassBuilder(
            originalTypeName = originalTypeName,
            originalTypeSpec = typeSpec,
            newTypeName = generatedClassName,
            generatedInterfaces = generatedInterfaces,
            scopeProviderMemberName = obtainScopeProviderMemberName(annotation, scopeProviders),
            freezeWrapper = annotation.freeze
        ).build()

        FileSpec.builder(originalTypeName.packageName, generatedClassName)
            .addType(classToGenerateSpec)
            .build()
            .writeTo(File(kaptGeneratedDir))

    }

    private fun obtainScopeProviderMemberName(
        annotation: ToNativeClass,
        scopeProviders: Map<ClassName, PropertySpec>
    ): MemberName? {
        //this is the dirtiest hack ever but it works :O
        //there probably is some way of doing this via kotlinpoet-metadata
        //https://area-51.blog/2009/02/13/getting-class-values-from-annotations-in-an-annotationprocessor/
        var typeMirror: KSType? = null
//        try {
            annotation.launchOnScope
//        } catch (e: MirroredTypeException) {
//            typeMirror = e.typeMirror
//        }
        if (typeMirror != null
            && typeMirror.toString() != "com.futuremind.koru.NoScopeProvider" //TODO do not compare strings but types
            && scopeProviders[typeMirror.toTypeName()] == null
        ) {
            throw IllegalStateException("$typeMirror can only be used in @ToNativeClass(launchOnScope) if it has been annotated with @ExportedScopeProvider")
        }
        return scopeProviders[typeMirror?.toTypeName()]?.let {
            MemberName(
                packageName = (typeMirror?.toTypeName() as ClassName).packageName,
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
            .mapTo(arrayListOf()) { it.declaration.getClassName(resolver) }
        if (!superTypeNames.contains(ScopeProvider::class.asTypeName())) {
            throw IllegalStateException("ExportedScopeProvider can only be applied to a class extending ScopeProvider interface")
        }
    }

}

internal fun KSDeclaration.getPackage(resolver: Resolver) =
    packageName.asString()

internal fun KSDeclaration.getClassName(resolver: Resolver) =
    ClassName(this.getPackage(resolver), this.simpleName.toString())

data class GeneratedInterface(val name: TypeName, val typeSpec: TypeSpec)

@KspExperimental
@KotlinPoetKspPreview
class ProcessorProvider: SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return Processor(environment)
    }
}
