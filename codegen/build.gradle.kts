import org.jlleitschuh.gradle.ktlint.reporter.ReporterType

plugins {
    alias(libs.plugins.axionRelease)
    alias(libs.plugins.kotlin)
    alias(libs.plugins.ktlint)
    `java-library`
    id("maven-publish")
}

scmVersion {
    versionIncrementer("incrementMinorIfNotOnRelease", mapOf("releaseBranchPattern" to "release/.+"))
    repository {
        directory.set(project.rootProject.file("../").path)
    }
}

group = "com.stackgen"
version = scmVersion.version

dependencies {
    implementation(libs.kotlinLogging)
    api(libs.kotlinPoet)
    implementation(libs.kotlinReflect)

    runtimeOnly(libs.logbackClassic)

    testRuntimeOnly(libs.jUnitPlatformLauncher)
    testImplementation(libs.jUnit)
    testImplementation(libs.kotestAssertions)
    testImplementation(libs.kotestRunner)

    // test jars
    testRuntimeOnly(libs.scribe)
}

ktlint {
    reporters {
        reporter(ReporterType.PLAIN)
        reporter(ReporterType.CHECKSTYLE)
    }
}

kotlin {
    jvmToolchain(17)
}

java {
    withJavadocJar()
    withSourcesJar()
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/stackgenhq/scala-kotlin-extension-codegen")
            credentials {
                username = project.findProperty("gradle.publish.key") as String? ?: System.getenv("GRADLE_PUBLISH_KEY")
                password = project.findProperty("gradle.publish.secret") as String? ?: System.getenv("GRADLE_PUBLISH_SECRET")
            }
        }
    }

    publications {
        register<MavenPublication>(project.name) {
            from(components["java"])
        }
    }
}
