package com.stackgen.codegen.scalakotlin.plugin

import com.squareup.kotlinpoet.TypeVariableName
import com.stackgen.codegen.ParameterMapping
import com.stackgen.codegen.ScalaKotlinExtensionCodegen
import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.net.URLClassLoader

@CacheableTask
abstract class GenerateKotlinExtensionsForScalaClassesTask : DefaultTask() {
    @get:Input
    @get:Optional
    abstract val classesDir: Property<File>

    @get:Internal
    abstract val parameterMappings: ListProperty<ParameterMapping>

    @get:Internal
    abstract val typeVariableMappings: MapProperty<TypeVariableName, TypeVariableName>

    // [typeVariableMappings] isn't directly serializable so Gradle cannot cache based on it. Use a hash input instead
    @get:Input
    val typeVariableMappingsHash: Int by lazy { typeVariableMappings.get().hashCode() }

    // [parameterMappings] isn't directly serializable so Gradle cannot cache based on it. Use a hash input instead
    @get:Input
    val parameterMappingsHash: Int by lazy { parameterMappings.get().hashCode() }

    @get:Input
    abstract val outputDirectory: Property<File>

    @get:InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    val codegenFiles: FileCollection = project.configurations.getByName("codegen")

    @get:OutputDirectory
    val output: Provider<File> by lazy { outputDirectory }

    @TaskAction
    fun action() {
        val generator = ScalaKotlinExtensionCodegen(
            typeVariableMappings = typeVariableMappings.orNull.orEmpty(),
            parameterMappings = parameterMappings.orNull.orEmpty(),
            outputDir = output.get(),
        )

        codegenJars(generator)

        if (classesDir.isPresent) {
            codegenClasses(generator)
        }
    }

    private fun codegenJars(generator: ScalaKotlinExtensionCodegen) {
        val config = project.configurations.getByName("codegen")
        val codegenJars = config.resolvedConfiguration.firstLevelModuleDependencies
            .flatMap { dep -> dep.moduleArtifacts.map { it.file }.filter { it.extension == "jar" } }
        val depJars = config.resolve().map { it.toURI().toURL() }.toTypedArray()

        // create a classloader which has all necessary dependencies to load the codegen classes
        val classLoader = URLClassLoader(depJars, ClassLoader.getSystemClassLoader())

        codegenJars.forEach { jar ->
            generator.generateKotlinExtensionsForClassesInJar(jar, classLoader)
        }
    }

    private fun codegenClasses(generator: ScalaKotlinExtensionCodegen) {
        val config = project.configurations.getByName("codegenClasspath")
        val codegenClasspath = config.resolve().map { it.toURI().toURL() }.toTypedArray()

        // create a classloader which has all necessary dependencies to load the codegen classes
        val classLoader =
            URLClassLoader(codegenClasspath + classesDir.get().toURI().toURL(), ClassLoader.getSystemClassLoader())
        generator.generateKotlinExtensionsForClassesInDir(classesDir.get(), classLoader)
    }
}
