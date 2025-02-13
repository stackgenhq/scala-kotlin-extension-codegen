package com.stackgen.codegen

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.asClassName
import com.stackgen.codegen.ScalaKotlinExtensionCodegen.Companion.EXTENSION_SUFFIX
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File
import java.lang.reflect.Modifier
import java.util.jar.JarEntry
import java.util.jar.JarFile
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KVisibility
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.full.staticFunctions
import kotlin.reflect.full.valueParameters
import kotlin.reflect.javaType
import kotlin.reflect.jvm.internal.KotlinReflectionInternalError
import kotlin.reflect.jvm.kotlinFunction
import kotlin.streams.asSequence

private val logger = KotlinLogging.logger {}

class ScalaExtensionFinder(private val classLoader: ClassLoader) {
    companion object {
        private val JarEntry.className: String
            get() = realName.removeSuffix(".class").replace('/', '.')

        /**
         * All classes contained in a [Class] (recursively including [Class.getClasses])
         *
         * Note: Using [Class.getClasses] here is able to more reliably load the classes than [KClass.nestedClasses]
         */
        private val Class<*>.allClasses: List<Class<*>>
            get() = if (classes.isEmpty()) listOf(this) else classes.flatMap { it.allClasses } + this

        /**
         * All scala extension [KFunction]s of a [KClass]
         *
         * We consider a function an extension if it's public, static, and its name ends in `$extension`
         *
         * Note: kotlin reflection on functions ([KClass.staticFunctions]) sometimes finds anonymous, typeless functions
         *  that cannot be reflected. Using Java's `methods` works around such cases...
         */
        private val KClass<*>.declaredExtensionFunctions: List<KFunction<*>>
            get() = java.methods
                .filter { m -> Modifier.isStatic(m.modifiers) && m.name.endsWith(EXTENSION_SUFFIX) }
                .mapNotNull { it.kotlinFunction }

        /**
         * Fully-qualified classname of some File by removing some prefix file path (eg the directory being scanned) and
         * replacing `/` with `.`
         */
        private fun File.toFullyQualifiedClassName(removePrefix: File) =
            "${parentFile.toRelativeString(removePrefix).replace('/', '.')}.$nameWithoutExtension"

        /**
         * Find [ExtensionFunction]s in [this] [KClass]
         */
        internal fun KClass<*>.getScalaExtensionFunctions(): List<ExtensionFunction> =
            declaredExtensionFunctions.map { extensionFunction ->
                val extensionType = constructors.single().valueParameters.single().type
                val instanceMethod = instanceFunctionForScalaExtension(this, extensionFunction)

                ExtensionFunction(
                    name = instanceMethod.name,
                    typeParameters = extensionFunction.typeParameters,
                    extensionType = extensionType,
                    returnType = extensionFunction.returnType,
                    // parameter names seem to be preserved in the non-extension version of the function, so use those
                    arguments = instanceMethod.valueParameters.toList(),
                )
            }

        /**
         * Find the instance method paired for the given extension
         *
         * Observed behavior is that there's always a `function$extension` static function and a `function` instance function.
         */
        private fun instanceFunctionForScalaExtension(containingClass: KClass<*>, extensionFunction: KFunction<*>) =
            containingClass.declaredMemberFunctions.single { f ->
                f.name == extensionFunction.name.removeSuffix(EXTENSION_SUFFIX) &&
                    f.visibility == KVisibility.PUBLIC &&
                    memberParametersMatchExtension(f, extensionFunction)
            }

        @OptIn(ExperimentalStdlibApi::class)
        private fun memberParametersMatchExtension(member: KFunction<*>, extensionFunction: KFunction<*>): Boolean =
            member.valueParameters.size == extensionFunction.valueParameters.size - 1 &&
                member.valueParameters.map { it.type.javaType.typeName } == extensionFunction.valueParameters.drop(1).map { it.type.javaType.typeName }
    }

    /**
     * Scan a jar [File]'s classes for extension functions
     */
    fun scanJar(jarFile: File): Map<ClassName, List<ExtensionFunction>> = JarFile(jarFile).use { jar ->
        logger.debug { "Processing classes in $jarFile" }
        val classesToLoad = jar.versionedStream().asSequence()
            .filter { !it.isDirectory && it.name.endsWith("class") && !it.className.endsWith('$') }
            .map { it.className }

        findExtensionsInClasses(classesToLoad).also { extensions ->
            logger.debug { "Found ${extensions.values.sumOf { it.size }} extensions in $jarFile" }
        }
    }

    /**
     * Scan a directory's classes for extension functions
     */
    fun scanDir(dir: File): Map<ClassName, List<ExtensionFunction>> {
        logger.debug { "Processing classes in $dir" }
        val classesToLoad =
            dir.walkTopDown()
                .filter { it.isFile && it.extension == "class" && !it.nameWithoutExtension.endsWith('$') }
                .map { it.toFullyQualifiedClassName(dir) }

        return findExtensionsInClasses(classesToLoad).also { extensions ->
            logger.debug { "Found ${extensions.values.sumOf { it.size }} extensions in $dir" }
        }
    }

    /**
     * Find any scala [ExtensionFunction] information in the given classes, grouped by the name of the containing class
     */
    internal fun findExtensionsInClasses(
        classesToLoad: Sequence<String>,
    ): Map<ClassName, List<ExtensionFunction>> {
        val classes =
            classesToLoad
                .flatMap { className -> classLoader.loadClass(className).allClasses.map { it.kotlin } }
                .filter { clazz ->
                    try {
                        clazz.declaredExtensionFunctions.any() && clazz.constructors.singleOrNull { c -> c.valueParameters.size == 1 } != null
                    } catch (e: Throwable) {
                        when (e) {
                            // scala generates some deeply nested classes that cause a number of issues when trying to load...
                            is IllegalStateException, is KotlinReflectionInternalError, is UnsupportedOperationException -> {
                                logger.warn(e) { "Unable to check ${clazz.asClassName()} for scala extensions! Skipping..." }
                                false
                            }
                            else -> throw e
                        }
                    }
                }
                .toList()
        logger.debug { "Found ${classes.size} candidate classes" }

        val extensionFunctions = classes.associate {
            val extensions = it.getScalaExtensionFunctions()
            val className = it.asClassName()
            logger.debug { "Found ${extensions.size} extension functions in $className" }
            className to extensions
        }
        return extensionFunctions
    }
}
