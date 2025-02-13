package com.stackgen.codegen

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.asTypeName
import com.squareup.kotlinpoet.asTypeVariableName
import com.squareup.kotlinpoet.buildCodeBlock
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File
import java.net.URLClassLoader

private val logger = KotlinLogging.logger {}

class ScalaKotlinExtensionCodegen(
    private val typeVariableMappings: Map<TypeVariableName, TypeVariableName> = emptyMap(),
    parameterMappings: List<ParameterMapping> = emptyList(),
    private val outputDir: File,
) {
    companion object {
        const val EXTENSION_SUFFIX = "\$extension"

        internal val suppressOnTypeVariableOverride: AnnotationSpec =
            AnnotationSpec.builder(Suppress::class).addMember(
                "%L",
                listOf(
                    "BOUNDS_NOT_ALLOWED_IF_BOUNDED_BY_TYPE_PARAMETER",
                    "TYPE_MISMATCH",
                    "INCONSISTENT_TYPE_PARAMETER_BOUNDS",
                    "UPPER_BOUND_VIOLATED",
                    "RETURN_TYPE_MISMATCH",
                    "TYPE_INFERENCE_EXPECTED_TYPE_MISMATCH",
                    "TYPE_INFERENCE_INCORPORATION_ERROR",
                    "ARGUMENT_TYPE_MISMATCH",
                    "NEW_INFERENCE_NO_INFORMATION_FOR_PARAMETER",
                ).joinToString { "\"$it\"" },
            ).build()

        private fun TypeName.stripAnnotations(): TypeName = if (this is ParameterizedTypeName) {
            copy(annotations = emptyList(), typeArguments = typeArguments.map { it.stripAnnotations() })
        } else {
            copy(annotations = emptyList())
        }
    }

    private val parameterMappings = parameterMappings.associateBy { it.originalType }

    /**
     * Generate kotlin extension functions wrappers into [outputDir] for any scala extensions found in classes in the
     * given [jarFile]
     */
    fun generateKotlinExtensionsForClassesInJar(
        jarFile: File,
        classLoader: ClassLoader = URLClassLoader(arrayOf(jarFile.toURI().toURL()), ClassLoader.getSystemClassLoader()),
    ) = generateKotlinExtensions(ScalaExtensionFinder(classLoader).scanJar(jarFile), outputDir)

    /**
     * Generate kotlin extension functions wrappers into [outputDir] for any scala extensions found in classes in the
     * given [directory]
     */
    fun generateKotlinExtensionsForClassesInDir(
        directory: File,
        classLoader: ClassLoader = URLClassLoader(arrayOf(directory.toURI().toURL()), ClassLoader.getSystemClassLoader()),
    ) = generateKotlinExtensions(ScalaExtensionFinder(classLoader).scanDir(directory), outputDir)

    internal fun generateKotlinExtensions(toGenerate: Map<ClassName, List<ExtensionFunction>>, outputDir: File) {
        logger.debug { "Generating extensions for ${toGenerate.size} classes into $outputDir" }
        val extensionFiles = toGenerate.map { (className, functions) ->
            val wrapperObject = wrapperObjectForClassExtensions(className, functions)
            FileSpec.builder(className.packageName, wrapperObject.name!!).addType(wrapperObject).build()
        }
        extensionFiles.forEach { file -> file.writeTo(outputDir) }
    }

    internal fun wrapperObjectForClassExtensions(
        wrappedClassName: ClassName,
        extensions: List<ExtensionFunction>,
    ): TypeSpec {
        val wrapperName = "${wrappedClassName.simpleName}Extensions"
        val objectExtensionFunctions = extensions.sorted().map { extensionFunctionToFunSpec(it, wrappedClassName) }
        val wrapperObject = TypeSpec.objectBuilder(wrapperName).addFunctions(objectExtensionFunctions).build()

        logger.debug {
            "Created extension wrapper object $wrapperName with ${objectExtensionFunctions.size} functions"
        }
        return wrapperObject
    }

    internal fun extensionFunctionToFunSpec(
        extensionFunction: ExtensionFunction,
        wrappedClassName: ClassName,
    ): FunSpec {
        val annotations = mutableListOf<AnnotationSpec>()
        val wrappedTypeVariables = mutableListOf<TypeVariableName>()
        val wrapperTypeVariables = mutableListOf<TypeVariableName>()

        extensionFunction.typeParameters.map { it.asTypeVariableName() }.forEach { typeVariable ->
            val mapping = typeVariableMappings[typeVariable]
            if (mapping != null) {
                // changing the type variable bounds means we should suppress a number of checks
                annotations.add(suppressOnTypeVariableOverride)

                // produce a renamed copy of the original variable for use as a type variable on the wrapped call
                val wrappedCopy = TypeVariableName("Wrapped${typeVariable.name}", typeVariable.bounds, typeVariable.variance)
                wrappedTypeVariables.add(wrappedCopy)

                wrapperTypeVariables.add(mapping)
                wrapperTypeVariables.add(wrappedCopy)
            } else {
                wrappedTypeVariables.add(typeVariable)
                wrapperTypeVariables.add(typeVariable)
            }
        }

        val parameters = extensionFunctionArgumentToParameterMapping(extensionFunction)
        val receiverType = extensionFunction.extensionType.asTypeName().stripAnnotations()
        val returnType = extensionFunction.returnType.asTypeName().stripAnnotations()

        val wrappedCallTypeConstraints =
            buildCodeBlock {
                if (wrappedTypeVariables.isNotEmpty()) {
                    add("<")
                    wrappedTypeVariables.forEachIndexed { index, typeVariable ->
                        if (index > 0) add(",·")
                        add("%T", typeVariable)
                    }
                    add(">")
                }
            }
        val parameterPassing =
            buildCodeBlock {
                parameters.forEach { (param, mapping) ->
                    add(",·") // '·' char is a special non-breaking space

                    if (mapping?.templatePrefix != null) {
                        add(mapping.templatePrefix, *mapping.templatePrefixParams.orEmpty().toTypedArray())
                    }

                    add("%N", param)

                    if (mapping?.templateSuffix != null) {
                        add(mapping.templateSuffix, *mapping.templateSuffixParams.orEmpty().toTypedArray())
                    }
                }
            }

        logger.debug { "Wrapping ${extensionFunction.name} from ${wrappedClassName.canonicalName}" }

        return FunSpec.builder(extensionFunction.name)
            .addAnnotations(annotations)
            .receiver(receiverType)
            .returns(returnType)
            .addTypeVariables(wrapperTypeVariables)
            .addParameters(parameters.keys)
            .addStatement(
                "return %T.`${extensionFunction.name}$EXTENSION_SUFFIX`%L(this%L)",
                wrappedClassName,
                wrappedCallTypeConstraints,
                parameterPassing,
            )
            .build()
    }

    /**
     * Calculate extension wrapper function's parameters and any conversion/mappings that must take place for [this]
     * function's arguments
     */
    private fun extensionFunctionArgumentToParameterMapping(extensionFunction: ExtensionFunction) =
        extensionFunction.arguments.mapIndexed { index, param ->
            val type = param.type.asTypeName().stripAnnotations()
            var mapping = parameterMappings[type]
            var newTypeName = mapping?.newTypeName ?: type

            // if no mapping found initially and the parameter is of some parameterized type, attempt to find mappings by
            // its unparameterized type or star type, then copy the parameters back to the mapped type
            if (mapping == null && type is ParameterizedTypeName) {
                val starType = type.copy(typeArguments = List(type.typeArguments.size) { STAR })
                mapping = parameterMappings[type.rawType] ?: parameterMappings[starType]

                newTypeName = mapping?.newTypeName?.let {
                    when (it) {
                        is ClassName -> it.parameterizedBy(type.typeArguments)
                        is ParameterizedTypeName -> it.copy(typeArguments = type.typeArguments)
                        else ->
                            throw IllegalArgumentException("Unable to project existing type arguments for $type onto provided mapping of $it")
                    }
                } ?: type
            }
            // if last parameter is an array, use varargs by default
            val paramSpec =
                if (index == extensionFunction.arguments.size - 1 &&
                    extensionFunction.arguments.size == 1 &&
                    (newTypeName as? ParameterizedTypeName)?.rawType == Array::class.asTypeName()
                ) {
                    ParameterSpec(param.name!!, newTypeName.typeArguments.single(), KModifier.VARARG)
                } else {
                    ParameterSpec(param.name!!, newTypeName)
                }

            paramSpec to mapping
        }.toMap()
}
