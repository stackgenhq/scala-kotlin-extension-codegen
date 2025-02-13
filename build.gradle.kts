import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import io.gitlab.arturbosch.detekt.Detekt
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType
import pl.allegro.tech.build.axion.release.domain.hooks.HookContext
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

plugins {
    alias(libs.plugins.axionRelease)
    alias(libs.plugins.kotlin) apply false
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.versionCheck)
}

scmVersion {
    versionIncrementer("incrementMinorIfNotOnRelease", mapOf("releaseBranchPattern" to "release/.+"))

    hooks {
        // Automate moving `[Unreleased]` changelog entries into `[<version>]` on release
        // FIXME - workaround for Kotlin DSL issue https://github.com/allegro/axion-release-plugin/issues/500
        val changelogPattern =
            "\\[Unreleased\\]([\\s\\S]+?)\\n" +
                "(?:^\\[Unreleased\\]: https:\\/\\/github\\.com\\/(\\S+\\/\\S+)\\/compare\\/[^\\n]*\$([\\s\\S]*))?\\z"
        pre(
            "fileUpdate",
            mapOf(
                "file" to "CHANGELOG.md",
                "pattern" to KotlinClosure2<String, HookContext, String>({ _, _ -> changelogPattern }),
                "replacement" to KotlinClosure2<String, HookContext, String>({ version, context ->
                    // github "diff" for previous version
                    val previousVersionDiffLink =
                        when (context.previousVersion == version) {
                            true -> "releases/tag/v$version" // no previous, just link to the version
                            false -> "compare/v${context.previousVersion}...v$version"
                        }
                    """
                        \[Unreleased\]

                        ## \[$version\] - $currentDateString$1
                        \[Unreleased\]: https:\/\/github\.com\/$2\/compare\/v$version...HEAD
                        \[$version\]: https:\/\/github\.com\/$2\/$previousVersionDiffLink$3
                    """.trimIndent()
                }),
            ),
        )

        pre("commit")
    }
}

group = "com.stackgen"
version = scmVersion.version

subprojects {
    apply {
        plugin(rootProject.libs.plugins.detekt.get().pluginId)
        plugin(rootProject.libs.plugins.ktlint.get().pluginId)
    }

    ktlint {
        debug.set(false)
        verbose.set(true)
        android.set(false)
        outputToConsole.set(true)
        ignoreFailures.set(false)
        enableExperimentalRules.set(true)
        filter {
            include("**/kotlin/**")
        }

        reporters {
            reporter(ReporterType.PLAIN)
            reporter(ReporterType.CHECKSTYLE)
        }
    }

    detekt {
        config.setFrom(rootProject.files("config/detekt/detekt.yml"))
    }
}

val currentDateString: String
    get() = OffsetDateTime.now(ZoneOffset.UTC).toLocalDate().format(DateTimeFormatter.ISO_DATE)

fun String.isNonStable() = "^[0-9,.v-]+(-r)?$".toRegex().matches(this).not()

tasks {
    withType<Detekt>().configureEach {
        reports {
            html.required.set(true)
            html.outputLocation.set(file("build/reports/detekt.html"))
        }
    }

    withType<DependencyUpdatesTask> {
        rejectVersionIf {
            candidate.version.isNonStable()
        }
    }

    register("reformatAll") {
        description = "Reformat all the Kotlin Code"

        dependsOn("ktlintFormat")
        dependsOn(gradle.includedBuild("codegen").task(":ktlintFormat"))
        dependsOn(gradle.includedBuild("plugin-build").task(":scala-kotlin-extension-codegen-gradle:ktlintFormat"))
    }

    register("preMerge") {
        description = "Runs all the tests/verification tasks on both top level and included build."

        dependsOn(":example:check")
        dependsOn(gradle.includedBuild("codegen").task(":check"))
        dependsOn(gradle.includedBuild("plugin-build").task(":scala-kotlin-extension-codegen-gradle:check"))
        dependsOn(gradle.includedBuild("plugin-build").task(":scala-kotlin-extension-codegen-gradle:validatePlugins"))
    }

    register("setupPluginUploadFromEnvironment") {
        doLast {
            val key = System.getenv("GRADLE_PUBLISH_KEY")
            val secret = System.getenv("GRADLE_PUBLISH_SECRET")

            if (key == null || secret == null) {
                throw GradleException("gradlePublishKey and/or gradlePublishSecret are not defined environment variables")
            }

            System.setProperty("gradle.publish.key", key)
            System.setProperty("gradle.publish.secret", secret)
        }
    }

    register("publishPlugins") {
        description = "Publishes the plugin to the Gradle Plugin Portal"
        group = "plugin portal"

        dependsOn(gradle.includedBuild("plugin-build").task(":scala-kotlin-extension-codegen-gradle:publishPlugins"))
    }

    register("publishAllPublicationsToGitHubPackagesRepository") {
        description = "Publishes each included build (codegen and plugin) to the GithubPackages repo"
        group = "publishing"

        dependsOn(gradle.includedBuild("codegen").task(":publishAllPublicationsToGitHubPackagesRepository"))
        dependsOn(gradle.includedBuild("plugin-build").task(":scala-kotlin-extension-codegen-gradle:publishAllPublicationsToGitHubPackagesRepository"))
    }

    register("clean") {
        description = "Clean all included builds"
        group = "build"

        project.allprojects.map { it.layout.buildDirectory }.forEach { delete(it) }

        dependsOn(gradle.includedBuild("codegen").task(":clean"))
        dependsOn(gradle.includedBuild("plugin-build").task(":scala-kotlin-extension-codegen-gradle:clean"))
    }

    wrapper {
        distributionType = Wrapper.DistributionType.ALL
    }
}
