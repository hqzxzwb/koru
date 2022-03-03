package com.futuremind.koruksp.processor

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.kspSourcesDir
import io.kotest.matchers.collections.*
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.reflect.KVisibility

class TypesGenerationTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `should generate interface from interface via @ToNativeClass`() {

        val generatedType = compileAndReturnGeneratedClass(
            source = SourceFile.kotlin(
                "interface1.kt",
                """
                            package com.futuremind.kmm101.test
                            
                            import com.futuremind.koruksp.ToNativeClass
                            import kotlinx.coroutines.flow.Flow

                            @ToNativeClass
                            interface InterfaceGenerationExample {
                                val someVal : Float
                                val someValFlow : Flow<Float>
                                fun blocking(whatever: Int) : Float
                                suspend fun suspending(whatever: Int) : Float
                                fun flow(whatever: Int) : Flow<Float>
                            }
                        """
            ),
            generatedClassCanonicalName = "com.futuremind.kmm101.test.InterfaceGenerationExample$defaultInterfaceNameSuffix",
            tempDir = tempDir
        )

        generatedType.java.isInterface shouldBe false
        generatedType.memberReturnType("someVal") shouldBe null
        generatedType.memberReturnType("someValFlow") shouldBe "com.futuremind.koruksp.FlowWrapper<kotlin.Float>"
        generatedType.memberReturnType("blocking") shouldBe null
        generatedType.memberReturnType("suspending") shouldBe "com.futuremind.koruksp.SuspendWrapper<kotlin.Float>"
        generatedType.memberReturnType("flow") shouldBe "com.futuremind.koruksp.FlowWrapper<kotlin.Float>"
    }


    @Test
    fun `should generate interface from class via @ToNativeClass`() {

        val generatedType = compileAndReturnGeneratedClass(
            source = SourceFile.kotlin(
                "interface2.kt",
                """
                            package com.futuremind.kmm101.test
                            
                            import com.futuremind.koruksp.ToNativeClass
                            import kotlinx.coroutines.flow.Flow

                            @ToNativeClass
                            class InterfaceGenerationExample {
                                val someVal : Float = TODO()
                                val someValFlow : Flow<Float> = TODO()
                                fun blocking(whatever: Int) : Float = TODO()
                                suspend fun suspending(whatever: Int) : Float = TODO()
                                fun flow(whatever: Int) : Flow<Float> = TODO()
                            }
                        """
            ),
            generatedClassCanonicalName = "com.futuremind.kmm101.test.InterfaceGenerationExample$defaultInterfaceNameSuffix",
            tempDir = tempDir
        )

        generatedType.java.isInterface shouldBe false
        generatedType.memberReturnType("someVal") shouldBe null
        generatedType.memberReturnType("someValFlow") shouldBe "com.futuremind.koruksp.FlowWrapper<kotlin.Float>"
        generatedType.memberReturnType("blocking") shouldBe null
        generatedType.memberReturnType("suspending") shouldBe "com.futuremind.koruksp.SuspendWrapper<kotlin.Float>"
        generatedType.memberReturnType("flow") shouldBe "com.futuremind.koruksp.FlowWrapper<kotlin.Float>"
    }

    @Test
    fun `should generate class from interface via @ToNativeClass`() {

        val generatedType = compileAndReturnGeneratedClass(
            source = SourceFile.kotlin(
                "interface4.kt",
                """
                            package com.futuremind.kmm101.test
                            
                            import com.futuremind.koruksp.ToNativeClass
                            import kotlinx.coroutines.flow.Flow

                            @ToNativeClass
                            interface ClassGenerationExample {
                                val someVal : Float
                                val someValFlow : Flow<Float>
                                fun blocking(whatever: Int) : Float
                                suspend fun suspending(whatever: Int) : Float
                                fun flow(whatever: Int) : Flow<Float>
                            }
                        """
            ),
            generatedClassCanonicalName = "com.futuremind.kmm101.test.ClassGenerationExample$defaultClassNameSuffix",
            tempDir = tempDir
        )

        generatedType.java.isInterface shouldBe false
        generatedType.memberReturnType("someVal") shouldBe null
        generatedType.memberReturnType("someValFlow") shouldBe "com.futuremind.koruksp.FlowWrapper<kotlin.Float>"
        generatedType.memberReturnType("blocking") shouldBe null
        generatedType.memberReturnType("suspending") shouldBe "com.futuremind.koruksp.SuspendWrapper<kotlin.Float>"
        generatedType.memberReturnType("flow") shouldBe "com.futuremind.koruksp.FlowWrapper<kotlin.Float>"
    }

    @Test
    fun `should generate class from class via @ToNativeClass`() {

        val generatedType = compileAndReturnGeneratedClass(
            source = SourceFile.kotlin(
                "class5.kt",
                """
                            package com.futuremind.kmm101.test
                            
                            import com.futuremind.koruksp.ToNativeClass
                            import kotlinx.coroutines.flow.Flow

                            @ToNativeClass
                            class ClassGenerationExample {
                                val someVal : Float = TODO()
                                val someValFlow : Flow<Float> = TODO()
                                fun blocking(whatever: Int) : Float = TODO()
                                suspend fun suspending(whatever: Int) : Float = TODO()
                                fun flow(whatever: Int) : Flow<Float> = TODO()
                            }
                        """
            ),
            generatedClassCanonicalName = "com.futuremind.kmm101.test.ClassGenerationExample$defaultClassNameSuffix",
            tempDir = tempDir
        )

        generatedType.java.isInterface shouldBe false
        generatedType.memberReturnType("someVal") shouldBe null
        generatedType.memberReturnType("someValFlow") shouldBe "com.futuremind.koruksp.FlowWrapper<kotlin.Float>"
        generatedType.memberReturnType("blocking") shouldBe null
        generatedType.memberReturnType("suspending") shouldBe "com.futuremind.koruksp.SuspendWrapper<kotlin.Float>"
        generatedType.memberReturnType("flow") shouldBe "com.futuremind.koruksp.FlowWrapper<kotlin.Float>"
    }

    @Test
    fun `should match generated class with generated interface if they matched in original code`() {

        val compilationResult = prepareCompilation(
            sourceFiles = listOf(
                SourceFile.kotlin(
                    "interface3.kt",
                    """
                            package com.futuremind.kmm101.test
                            
                            import com.futuremind.koruksp.ToNativeClass
                            import kotlinx.coroutines.flow.Flow

                            @ToNativeClass
                            interface IExample {
                                val someVal : Float
                                val someValFlow : Flow<Float>
                                fun blocking(whatever: Int) : Float
                                suspend fun suspending(whatever: Int) : Float
                                fun flow(whatever: Int) : Flow<Float>
                            }
                        """
                ),
                SourceFile.kotlin(
                    "class3.kt",
                    """
                            package com.futuremind.kmm101.test
                            
                            import com.futuremind.koruksp.ToNativeClass
                            import kotlinx.coroutines.flow.Flow

                            @ToNativeClass
                            class Example : IExample {
                                override val someVal : Float = TODO()
                                override val someValFlow : Flow<Float> = TODO()
                                override fun blocking(whatever: Int) : Float = TODO()
                                override suspend fun suspending(whatever: Int) : Float = TODO()
                                override fun flow(whatever: Int) : Flow<Float> = TODO()
                            }
                        """
                )
            ),
            tempDir = tempDir
        ).compileWithKsp()

        val generatedInterface =
            compilationResult.classLoader.loadClass("com.futuremind.kmm101.test.IExample$defaultInterfaceNameSuffix").kotlin
        val generatedClass =
            compilationResult.classLoader.loadClass("com.futuremind.kmm101.test.Example$defaultClassNameSuffix").kotlin

        compilationResult.exitCode shouldBe KotlinCompilation.ExitCode.OK
        generatedInterface.java.isInterface shouldBe false
        generatedClass.java.isInterface shouldBe false

    }

    @Test
    fun `should not wrap private members when generating class`() {

        compileAndReturnGeneratedClass(
            source = SourceFile.kotlin(
                "privateClass.kt",
                """
                            package com.futuremind.kmm101.test
                            
                            import com.futuremind.koruksp.ToNativeClass
                            import kotlinx.coroutines.flow.Flow

                            @ToNativeClass
                            class PrivateFunctionsExample {
                                val someVal : Float = TODO()
                                val someValFlow : Flow<Float> = TODO()
                                fun blocking(whatever: Int) : Float = TODO()
                                suspend fun suspending(whatever: Int) : Float = TODO()
                                fun flow(whatever: Int) : Flow<Float> = TODO()
                                private val someValPrivate : Float = TODO()
                                private val someValFlowPrivate : Flow<Float> = TODO()
                                private fun blockingPrivate(whatever: Int) : Float = TODO()
                                private suspend fun suspendingPrivate(whatever: Int) : Float = TODO()
                                private fun flowPrivate(whatever: Int) : Flow<Float> = TODO()
                            }
                        """
            ),
            generatedClassCanonicalName = "com.futuremind.kmm101.test.PrivateFunctionsExample$defaultClassNameSuffix",
            tempDir = tempDir
        )
        //enough to check it compiles, it would not with wrapped private function

    }

    @Test
    fun `should not wrap private functions when generating interface`() {

        compileAndReturnGeneratedClass(
            source = SourceFile.kotlin(
                "privateInterface.kt",
                """
                            package com.futuremind.kmm101.test
                            
                            import com.futuremind.koruksp.ToNativeClass
                            import kotlinx.coroutines.flow.Flow

                            @ToNativeClass
                            class PrivateFunctionsExample {
                                fun blocking(whatever: Int) : Float = TODO()
                                suspend fun suspending(whatever: Int) : Float = TODO()
                                fun flow(whatever: Int) : Flow<Float> = TODO()
                                private fun blockingPrivate(whatever: Int) : Float = TODO()
                                private suspend fun suspendingPrivate(whatever: Int) : Float = TODO()
                                private fun flowPrivate(whatever: Int) : Flow<Float> = TODO()
                            }
                        """
            ),
            generatedClassCanonicalName = "com.futuremind.kmm101.test.PrivateFunctionsExample$defaultInterfaceNameSuffix",
            tempDir = tempDir
        )
        //enough to check it compiles, it would not with wrapped private function

    }

    @Test
    fun `should throw on interface generation from private type`() = testThrowsCompilationError(
        source = SourceFile.kotlin(
            "private.kt",
            """
                        package com.futuremind.kmm101.test
                        
                        import com.futuremind.koruksp.ToNativeClass

                        @ToNativeClass
                        private class PrivateClassExample {
                            suspend fun suspending(whatever: Int) : Float = TODO()
                        }
                        """
        ),
        expectedMessage = "Cannot wrap types with `private` modifier. Consider using internal or public.",
        tempDir = tempDir
    )

    @Test
    fun `should throw on class generation from private type`() = testThrowsCompilationError(
        source = SourceFile.kotlin(
            "private.kt",
            """
                        package com.futuremind.kmm101.test
                        
                        import com.futuremind.koruksp.ToNativeClass

                        @ToNativeClass
                        private class PrivateClassExample {
                            suspend fun suspending(whatever: Int) : Float = TODO()
                        }
                        """
        ),
        expectedMessage = "Cannot wrap types with `private` modifier. Consider using internal or public.",
        tempDir = tempDir
    )

    @Test
    fun `should freeze wrapper if freeze=true in annotation`() {

        val classToWrap = SourceFile.kotlin(
            "freeze1.kt",
            """
                        package com.futuremind.kmm101.test
                        
                        import com.futuremind.koruksp.ToNativeClass
                        import kotlinx.coroutines.flow.Flow
                        
                            @ToNativeClass(freeze = true)
                            class FreezeExample {
                                suspend fun suspending(whatever: Int) : Float = TODO()
                                fun flow(whatever: Int) : Flow<Float> = TODO()
                            }
                    """
        )

        val compilation = prepareCompilation(
            sourceFiles = listOf(classToWrap),
            tempDir = tempDir
        )
        val compilationResult = compilation.compile()

        compilationResult.exitCode shouldBe KotlinCompilation.ExitCode.OK

        val generatedClass = compilation.kspSourcesDir.walkBottomUp()
            .toList()
            .getContentByFilename("FreezeExample$defaultFileNameSuffix.kt")

        generatedClass shouldContain Regex("FlowWrapper<Float>\\s*=\\s*FlowWrapper\\(scopeProvider,\\s*true")
        generatedClass shouldContain "SuspendWrapper(scopeProvider, true"
    }

    @Test
    fun `should not freeze wrapper by default`() {

        val classToWrap = SourceFile.kotlin(
            "freeze2.kt",
            """
                        package com.futuremind.kmm101.test
                        
                        import com.futuremind.koruksp.ToNativeClass
                        import kotlinx.coroutines.flow.Flow
                        
                            @ToNativeClass
                            class FreezeExample {
                                suspend fun suspending(whatever: Int) : Float = TODO()
                                fun flow(whatever: Int) : Flow<Float> = TODO()
                            }
                    """
        )

        val compilation = prepareCompilation(
            sourceFiles = listOf(classToWrap),
            tempDir = tempDir
        )
        val compilationResult = compilation.compile()

        compilationResult.exitCode shouldBe KotlinCompilation.ExitCode.OK

        val generatedClass = compilation.kspSourcesDir.walkBottomUp().toList()
            .getContentByFilename("FreezeExample$defaultFileNameSuffix.kt")

        generatedClass shouldContain Regex("FlowWrapper<Float>\\s*=\\s*FlowWrapper\\(scopeProvider,\\s*false")
        generatedClass shouldContain "SuspendWrapper(scopeProvider, false"
    }

}