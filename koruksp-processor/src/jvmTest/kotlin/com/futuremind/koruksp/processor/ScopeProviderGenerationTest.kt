package com.futuremind.koruksp.processor

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.kspSourcesDir
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ScopeProviderGenerationTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `should throw if @ExportedScopeProvider is applied to class which doesn't extend ScopeProvider`() =
        testThrowsCompilationError(
            source = SourceFile.kotlin(
                "scopeProvider1.kt",
                """
                package com.futuremind.kmm101.test
                
                import com.futuremind.koruksp.ExportedScopeProvider
                import com.futuremind.koruksp.ScopeProvider
                import kotlinx.coroutines.MainScope
                
                @ExportedScopeProvider
                class MainScopeProvider {
                    override val scope = MainScope()
                }
            """
            ),
            expectedMessage = "ExportedScopeProvider can only be applied to a class extending ScopeProvider interface",
            tempDir = tempDir
        )

    @Test
    fun `should generate top level property with scope provider`() {

        val source = SourceFile.kotlin(
            "scopeProvider2.kt",
            """
                package com.futuremind.kmm101.test
                
                import com.futuremind.koruksp.ExportedScopeProvider
                import com.futuremind.koruksp.ScopeProvider
                import kotlinx.coroutines.MainScope
                
                @ExportedScopeProvider
                class MainScopeProvider : ScopeProvider {
                    override val scope = MainScope()
                }
            """
        )

        val compilation = prepareCompilation(source, tempDir)
        val compilationResult = compilation.compile()

        val generatedFiles = compilation.kspSourcesDir.walkBottomUp().toList()
        val generatedScopeProvider = generatedFiles
            .getContentByFilename("MainScopeProviderContainer.kt")

        compilationResult.exitCode shouldBe KotlinCompilation.ExitCode.OK
        generatedScopeProvider shouldBe
                """
                    |package com.futuremind.kmm101.test
                    |
                    |public val exportedScopeProvider_mainScopeProvider: MainScopeProvider = MainScopeProvider()
                """.trimMargin().trim()

    }

    @Test
    fun `should use generated scope provider via @ToNativeClass(launchOnScope)`() {

        val scopeProvider = SourceFile.kotlin(
            "scopeProvider3.kt",
            """
                        package com.futuremind.kmm101.test
                        
                        import com.futuremind.koruksp.ExportedScopeProvider
                        import com.futuremind.koruksp.ScopeProvider
                        import kotlinx.coroutines.MainScope
                        
                        @ExportedScopeProvider
                        class MainScopeProvider : ScopeProvider {
                            override val scope = MainScope()
                        }
                    """
        )

        val classToWrap = SourceFile.kotlin(
            "wrapper.kt",
            """
                        package com.futuremind.kmm101.test
                        
                        import com.futuremind.koruksp.ToNativeClass
                        import kotlinx.coroutines.flow.Flow
                        
                            @ToNativeClass(launchOnScope = MainScopeProvider::class)
                            class ImplicitScopeExample {
                                fun blocking(whatever: Int) : Float = TODO()
                                suspend fun suspending(whatever: Int) : Float = TODO()
                                fun flow(whatever: Int) : Flow<Float> = TODO()
                            }
                    """
        )

        val compilation = prepareCompilation(
            sourceFiles = listOf(scopeProvider, classToWrap),
            tempDir = tempDir
        )
        val compilationResult = compilation.compile()

        compilationResult.exitCode shouldBe KotlinCompilation.ExitCode.OK

        val generatedFiles = compilation.kspSourcesDir.walkBottomUp().toList()
        val generatedScopeProvider = generatedFiles
            .getContentByFilename("MainScopeProviderContainer.kt")

        val generatedClass = generatedFiles
            .getContentByFilename("ImplicitScopeExample$defaultFileNameSuffix.kt")

        generatedScopeProvider shouldContain "public val exportedScopeProvider_mainScopeProvider: MainScopeProvider = MainScopeProvider()"
        generatedClass shouldContain "FlowWrapper(scopeProvider, "
        generatedClass shouldContain "SuspendWrapper(scopeProvider, "

    }

    @Test
    fun `should import generated scope provider when different package`() {

        val scopeProvider = SourceFile.kotlin(
            "scopeProvider3.kt",
            """
                        package com.futuremind.kmm101.test.scope
                        
                        import com.futuremind.koruksp.ExportedScopeProvider
                        import com.futuremind.koruksp.ScopeProvider
                        import kotlinx.coroutines.MainScope
                        
                        @ExportedScopeProvider
                        class MainScopeProvider : ScopeProvider {
                            override val scope = MainScope()
                        }
                    """
        )

        val classToWrap = SourceFile.kotlin(
            "wrapper.kt",
            """
                        package com.futuremind.kmm101.test
                        
                        import com.futuremind.koruksp.ToNativeClass
                        import kotlinx.coroutines.flow.Flow
                        import com.futuremind.kmm101.test.scope.MainScopeProvider
                        
                            @ToNativeClass(launchOnScope = MainScopeProvider::class)
                            class ImplicitScopeExample {
                                fun blocking(whatever: Int) : Float = TODO()
                                suspend fun suspending(whatever: Int) : Float = TODO()
                                fun flow(whatever: Int) : Flow<Float> = TODO()
                            }
                    """
        )

        val compilation = prepareCompilation(
            sourceFiles = listOf(scopeProvider, classToWrap),
            tempDir = tempDir
        )
        val compilationResult = compilation.compile()

        compilationResult.exitCode shouldBe KotlinCompilation.ExitCode.OK

        val generatedFiles = compilation.kspSourcesDir.walkBottomUp().toList()
        val generatedScopeProvider = generatedFiles
            .getContentByFilename("MainScopeProviderContainer.kt")

        val generatedClass = generatedFiles
            .getContentByFilename("ImplicitScopeExample$defaultFileNameSuffix.kt")

        generatedScopeProvider shouldContain "public val exportedScopeProvider_mainScopeProvider: MainScopeProvider = MainScopeProvider()"
        generatedClass shouldContain "import com.futuremind.kmm101.test.scope.exportedScopeProvider_mainScopeProvider"
        generatedClass shouldContain Regex("FlowWrapper<Float>\\s*=\\s*FlowWrapper\\(scopeProvider, ")
        generatedClass shouldContain "SuspendWrapper(scopeProvider, "
    }

    @Test
    fun `should throw if trying to use @ToNativeClass(launchOnScope) without exporting scope via @ExportedScopeProvider`() {

        testThrowsCompilationError(
            sources = listOf(
                SourceFile.kotlin(
                    "generatedClass4.kt",
                    """
                        package com.futuremind.kmm101.test
                        
                        import com.futuremind.koruksp.ToNativeClass
                        import kotlinx.coroutines.flow.Flow
                        
                            @ToNativeClass(launchOnScope = MainScopeProvider::class)
                            class ImplicitScopeExample {
                                fun blocking(whatever: Int) : Float = TODO()
                                suspend fun suspending(whatever: Int) : Float = TODO()
                                fun flow(whatever: Int) : Flow<Float> = TODO()
                            }
                    """
                ),
                SourceFile.kotlin(
                    "scopeProvider4.kt",
                    """
                        package com.futuremind.kmm101.test

                        import com.futuremind.koruksp.ScopeProvider
                        import kotlinx.coroutines.MainScope

                        class MainScopeProvider : ScopeProvider {
                            override val scope = MainScope()
                        }
                    """
                )
            ),
            expectedMessage = "com.futuremind.kmm101.test.MainScopeProvider can only be used in @ToNativeClass(launchOnScope) if it has been annotated with @ExportedScopeProvider",
            tempDir = tempDir
        )


    }

}
