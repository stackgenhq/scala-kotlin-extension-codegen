pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

// "meta" because the root project contains nothing of substance, mostly just coordinates the composite builds for the codegen and plugin
rootProject.name = "scala-kotlin-extension-codegen-meta"

include(":example")
includeBuild(".")
// separate build to enable dependency substitution
// see: https://docs.gradle.org/current/userguide/composite_builds.html#included_build_declaring_substitutions
includeBuild("codegen")
includeBuild("plugin-build")
