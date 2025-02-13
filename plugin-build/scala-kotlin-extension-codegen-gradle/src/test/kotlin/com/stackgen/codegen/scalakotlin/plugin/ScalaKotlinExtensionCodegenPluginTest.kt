package com.stackgen.codegen.scalakotlin.plugin

import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test

class ScalaKotlinExtensionCodegenPluginTest {
    @Test
    fun `plugin is applied correctly to the project`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("com.stackgen.codegen.scalakotlin")

        val task = project.tasks.getByName("generateKotlinExtensionsForScalaClasses")
        assert(task is GenerateKotlinExtensionsForScalaClassesTask)
    }
}
