plugins {
    kotlin("jvm")
    `java-gradle-plugin`
    alias(libs.plugins.pluginPublish)
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(gradleApi())
    implementation("com.stackgen:scala-kotlin-extension-codegen:${scmVersion.version}")
    runtimeOnly(libs.scalaLibrary)

    testRuntimeOnly(libs.jUnitPlatformLauncher)
    testImplementation(libs.jUnit)
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}

gradlePlugin {
    website.set(property("WEBSITE").toString())
    vcsUrl.set(property("VCS_URL").toString())

    plugins {
        create(property("ID").toString()) {
            id = property("ID").toString()
            implementationClass = property("IMPLEMENTATION_CLASS").toString()
            version = scmVersion.version
            description = property("DESCRIPTION").toString()
            displayName = property("DISPLAY_NAME").toString()
            tags.set(listOf("codegen", "scala", "kotlin", "interop", "wrappers"))
        }
    }
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
}
