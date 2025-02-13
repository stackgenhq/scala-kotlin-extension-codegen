package com.stackgen.codegen.scalakotlin.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project

abstract class ScalaKotlinExtensionCodegenPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.configurations.register("codegen")
        project.configurations.register("codegenClasspath")

        project.tasks.register(
            "generateKotlinExtensionsForScalaClasses",
            GenerateKotlinExtensionsForScalaClassesTask::class.java,
        ) {
            it.group = "other"
            it.description =
                "Generates kotlin extension function wrappers around classes with scala extension functions"
        }
    }
}
