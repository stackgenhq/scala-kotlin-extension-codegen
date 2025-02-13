package com.stackgen.codegen

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.asTypeName
import com.stackgen.codegen.ScalaKotlinExtensionCodegen.Companion.EXTENSION_SUFFIX
import com.stackgen.codegen.ScalaKotlinExtensionCodegen.Companion.suppressOnTypeVariableOverride
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlin.reflect.typeOf

// class for getting KTypeParameter
class TypedClass<T>

// functions for getting KParameters
fun parameterFunction1(param: Int) = Unit
fun parameterFunction2(param1: Int, param2: Int) = Unit
fun parameterFunction3(param1: Int, param2: String) = Unit
fun parameterFunction4(param1: String, param2: Int) = Unit
fun parameterFunction5(param1: String, param2: String) = Unit

class ExtensionFunctionToFunSpecTest : BehaviorSpec({
    val someClassName = ClassName("", "SomeClass")

    Given("A basic function") {
        val codegen = ScalaKotlinExtensionCodegen(outputDir = tempdir())
        val extension = ExtensionFunction(
            name = "myFun",
            typeParameters = emptyList(),
            extensionType = typeOf<String>(),
            returnType = typeOf<Any>(),
            arguments = emptyList(),
        )

        When("Create wrapper FunSpec") {
            val funSpec = codegen.extensionFunctionToFunSpec(extension, someClassName)

            Then("It should equal") {
                val expected =
                    FunSpec.builder("myFun")
                        .receiver(String::class)
                        .returns(Any::class)
                        .addStatement("return SomeClass.`myFun$EXTENSION_SUFFIX`(this)")
                        .build()
                funSpec shouldBe expected
            }
        }
    }

    Given("A function with an argument") {
        val codegen = ScalaKotlinExtensionCodegen(outputDir = tempdir())

        val extension = ExtensionFunction(
            name = "myFun",
            typeParameters = emptyList(),
            extensionType = typeOf<String>(),
            returnType = typeOf<Any>(),
            arguments = ::parameterFunction1.parameters,
        )

        When("Create wrapper FunSpec") {
            val funSpec = codegen.extensionFunctionToFunSpec(extension, someClassName)

            Then("It should have an Int parameter") {
                val expected =
                    FunSpec.builder("myFun")
                        .receiver(String::class)
                        .returns(Any::class)
                        .addParameter("param", Int::class)
                        .addStatement("return SomeClass.`myFun$EXTENSION_SUFFIX`(this, `param`)")
                        .build()
                funSpec shouldBe expected
            }
        }
    }

    Given("A parameterized function with a parameter mapping") {
        val codegen = ScalaKotlinExtensionCodegen(
            parameterMappings = listOf(
                ParameterMapping(
                    originalType = Int::class.asClassName(),
                    newTypeName = Double::class.asClassName(),
                    templateSuffix = ".toInt()",
                ),
            ),
            outputDir = tempdir(),
        )
        val extension = ExtensionFunction(
            name = "myFun",
            typeParameters = emptyList(),
            extensionType = typeOf<String>(),
            returnType = typeOf<Any>(),
            arguments = ::parameterFunction1.parameters,
        )

        When("Create wrapper FunSpec") {
            val funSpec = codegen.extensionFunctionToFunSpec(extension, someClassName)

            Then("It should have a Double param which is mapped using toInt") {
                val expected =
                    FunSpec.builder("myFun")
                        .receiver(String::class)
                        .returns(Any::class)
                        .addParameter("param", Double::class)
                        .addStatement("return SomeClass.`myFun$EXTENSION_SUFFIX`(this, `param`.toInt())")
                        .build()
                funSpec shouldBe expected
            }
        }
    }

    Given("A generic function") {
        val codegen = ScalaKotlinExtensionCodegen(outputDir = tempdir())
        val extension = ExtensionFunction(
            name = "myFun",
            typeParameters = TypedClass::class.typeParameters,
            extensionType = typeOf<String>(),
            returnType = typeOf<Any>(),
            arguments = emptyList(),
        )

        When("Create wrapper FunSpec") {
            val funSpec = codegen.extensionFunctionToFunSpec(extension, someClassName)

            Then("It should have the type parameter") {
                val expected =
                    FunSpec.builder("myFun")
                        .addTypeVariable(TypeVariableName("T"))
                        .receiver(String::class)
                        .returns(Any::class)
                        .addStatement("return SomeClass.`myFun$EXTENSION_SUFFIX`<T>(this)")
                        .build()
                funSpec shouldBe expected
            }
        }
    }

    Given("A generic function with a type variable mapping") {
        val codegen = ScalaKotlinExtensionCodegen(
            typeVariableMappings = mapOf(TypeVariableName("T") to TypeVariableName("V")),
            outputDir = tempdir(),
        )
        val extension = ExtensionFunction(
            name = "myFun",
            typeParameters = TypedClass::class.typeParameters,
            extensionType = typeOf<String>(),
            returnType = typeOf<Any>(),
            arguments = emptyList(),
        )

        When("Create wrapper FunSpec") {
            val funSpec = codegen.extensionFunctionToFunSpec(extension, someClassName)

            Then("It should have the mapped type parameter") {
                val expected =
                    FunSpec.builder("myFun")
                        .addAnnotation(suppressOnTypeVariableOverride)
                        .addTypeVariable(TypeVariableName("V"))
                        .addTypeVariable(TypeVariableName("WrappedT"))
                        .receiver(String::class)
                        .returns(Any::class)
                        .addStatement("return SomeClass.`myFun$EXTENSION_SUFFIX`<WrappedT>(this)")
                        .build()
                funSpec shouldBe expected
            }
        }
    }

    Given("Several functions of the same type, name, and arity") {
        val codegen = ScalaKotlinExtensionCodegen(outputDir = tempdir())
        val baseFun = ExtensionFunction("myFun", emptyList(), typeOf<String>(), typeOf<Any>(), emptyList())
        val extensions = listOf(
            ::parameterFunction4,
            ::parameterFunction1,
            ::parameterFunction5,
            ::parameterFunction2,
            ::parameterFunction3,
        ).map { f -> baseFun.copy(arguments = f.parameters) }

        When("Generate wrapper object for extensions") {
            val wrapperObject = codegen.wrapperObjectForClassExtensions(someClassName, extensions)

            Then("Extensions in wrapper should be in order or argument types") {
                val got = wrapperObject.funSpecs.map { f -> f.parameters.map { p -> p.type } }
                val expected = listOf(
                    ::parameterFunction1,
                    ::parameterFunction2,
                    ::parameterFunction3,
                    ::parameterFunction4,
                    ::parameterFunction5,
                ).map { f -> f.parameters.map { it.type.asTypeName() } }

                got shouldContainExactly expected
            }
        }
    }
})
