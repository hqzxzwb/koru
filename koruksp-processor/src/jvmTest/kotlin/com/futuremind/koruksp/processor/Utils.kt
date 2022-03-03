package com.futuremind.koruksp.processor

import com.google.devtools.ksp.KspExperimental
import com.squareup.kotlinpoet.ksp.KotlinPoetKspPreview
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.kspSourcesDir
import com.tschuchort.compiletesting.symbolProcessorProviders
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.jetbrains.kotlin.descriptors.runtime.structure.parameterizedTypeArguments
import org.jetbrains.kotlin.util.capitalizeDecapitalize.capitalizeAsciiOnly
import java.io.File
import java.lang.reflect.GenericArrayType
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.TypeVariable
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.typeOf

fun testThrowsCompilationError(
    source: SourceFile,
    expectedMessage: String,
    tempDir: File
) = testThrowsCompilationError(listOf(source), expectedMessage, tempDir)

fun testThrowsCompilationError(
    sources: List<SourceFile>,
    expectedMessage: String,
    tempDir: File
) {
    val compilationResult = prepareCompilation(sources, tempDir).compile()
    compilationResult.exitCode shouldBe KotlinCompilation.ExitCode.COMPILATION_ERROR
    compilationResult.messages shouldContain expectedMessage
}

fun compileAndReturnGeneratedClass(
    source: SourceFile,
    generatedClassCanonicalName: String,
    tempDir: File
): KClass<out Any> {
    val compilationResult = prepareCompilation(source, tempDir).compileWithKsp()
//    debugPrintGenerated(compilationResult)
    val generatedClass = compilationResult.classLoader.loadClass(generatedClassCanonicalName)
    compilationResult.exitCode shouldBe KotlinCompilation.ExitCode.OK
    return generatedClass.kotlin
}

fun debugPrintGenerated(compilationResult: KotlinCompilation.Result) {
    compilationResult.generatedFiles.forEach {
        println("\n\n"+it.absolutePath+"\n")
        println(it.readText().trim())
    }
}


fun prepareCompilation(
    sourceFile: SourceFile,
    tempDir: File
) = prepareCompilation(listOf(sourceFile), tempDir)

@OptIn(KspExperimental::class, KotlinPoetKspPreview::class)
fun prepareCompilation(
    sourceFiles: List<SourceFile>,
    tempDir: File
) = KotlinCompilation()
    .apply {
        workingDir = tempDir
        symbolProcessorProviders = listOf(ProcessorProvider())
        inheritClassPath = true
        sources = sourceFiles
        verbose = false
    }

fun KotlinCompilation.compileWithKsp(): KotlinCompilation.Result {
    compile()
    val kspSources = kspSourcesDir.walkTopDown()
        .mapNotNullTo(arrayListOf()) {
            if (it.isFile) {
                SourceFile.kotlin(it.name, it.readText())
            } else {
                null
            }
        }
    kspSources.addAll(sources)
    val compilation2 = prepareCompilation(kspSources, workingDir)
    compilation2.symbolProcessorProviders = listOf()
    val compilationResult = compilation2.compile()
    return compilationResult
}

fun KClass<*>.member(methodName: String): Method? {
    val funName = methodName + "Native"
    val getterName = "get" + funName.capitalizeAsciiOnly()
    return java.declaredMethods.firstOrNull { it.name == funName || it.name == getterName }
}

fun KClass<*>.memberReturnType(methodName: String): String? {
    val javaType = member(methodName)?.genericReturnType ?: return null
    return javaType.kotlinName
}

val Type.kotlinName: String
    get() {
        if (this is Class<*>) {
            if (this.isArray) {
                return "Array<${this.componentType.kotlinName}>"
            }
            return this.kotlin.qualifiedName!!
        }
        if (this is ParameterizedType) {
            return "${rawType.kotlinName}<${parameterizedTypeArguments.joinToString { it.kotlinName }}>"
        }
        if (this is GenericArrayType) {
            return "Array<${this.genericComponentType.kotlinName}>"
        }
        if (this is TypeVariable<*>) {
            return name
        }
        throw IllegalArgumentException()
    }

fun Collection<File>.getContentByFilename(filename: String) = this
    .find { it.name == filename }!!
    .readText()
    .trim()

const val defaultClassNameSuffix = "NativeKt"
const val defaultFileNameSuffix = "Native"
const val defaultInterfaceNameSuffix = "NativeKt"