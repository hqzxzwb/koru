plugins {
    `kotlin-dsl`
    id("maven-publish")
    id("signing")
    id("org.jetbrains.dokka") version "1.5.30"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.dokka:dokka-gradle-plugin:1.5.30")
}

gradlePlugin {
    plugins {
        create("publish") {
            id = "com.futuremind.koruksp.publish"
            implementationClass = "com.futuremind.koruksp.gradle.PublishPlugin"
        }
    }
}
