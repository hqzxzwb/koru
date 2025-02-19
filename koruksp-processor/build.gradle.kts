plugins {
    /*
        this could be a pure-jvm module, but there are some dependency issues
        https://stackoverflow.com/questions/65830632/cant-access-commonmain-multiplatform-classes-from-a-jvm-only-module
     */
    kotlin("multiplatform") version "1.6.10"
    id("java-library")
    id("maven-publish")
    id("com.futuremind.koruksp.publish")
}

kotlin {

    //this is only used as kapt (annotation processor, so pure jvm)
    jvm {
        val main by compilations.getting {
            kotlinOptions {
                jvmTarget = "1.8"
            }
        }
    }

    sourceSets {

        val jvmMain by getting {
            dependencies {

                implementation(project(":koruksp"))

                //code generation
                val kotlinpoetVersion = "1.10.2"
                implementation("com.squareup:kotlinpoet:$kotlinpoetVersion")
                implementation("com.squareup:kotlinpoet-ksp:$kotlinpoetVersion")
                implementation("com.squareup:kotlinpoet-metadata:$kotlinpoetVersion")
                implementation("com.google.devtools.ksp:symbol-processing-api:1.6.10-1.0.2")

                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.0")
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation("org.junit.jupiter:junit-jupiter-api:5.8.1")
                runtimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.1")
                implementation("com.github.tschuchortdev:kotlin-compile-testing-ksp:1.4.7")
                implementation("io.kotest:kotest-assertions-core:4.6.3")
            }
        }

    }
}

koruPublishing {
    pomName = "Koru - Processor"
    pomDescription = "Wrappers for suspend functions / Flow in Kotlin Native - annotation processor."
}

//otherwise junit5 tests cannot be run from jvmTest
tasks.withType<Test> {
    useJUnitPlatform()
}
