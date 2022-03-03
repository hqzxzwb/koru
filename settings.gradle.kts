rootProject.name = "Koru"
gradle.rootProject {
    group = "com.github.hqzxzwb"
    version = "0.12.1"
}


includeBuild("gradlePlugins")
include(":koruksp")
include(":koruksp-processor")

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        maven("https://dl.bintray.com/kotlin/kotlin-eap")
        maven("https://plugins.gradle.org/m2/")
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}