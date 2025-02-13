import com.squareup.kotlinpoet.MemberName.Companion.member
import com.squareup.kotlinpoet.asTypeName
import com.stackgen.codegen.ParameterMapping
import org.jetbrains.kotlin.gradle.utils.extendsFrom
import scala.collection.immutable.Seq
import scala.jdk.javaapi.CollectionConverters

plugins {
    alias(libs.plugins.kotlin)
    id("com.stackgen.codegen.scalakotlin")
}

val generatedSourcesDir = "build/generated/extensions/main/kotlin"

// codegen deps must be available on classpath at runtime for the classloader, at compile time for the generated code
configurations.implementation.extendsFrom(configurations.codegen)

dependencies {
    codegen(libs.scribe)
}

kotlin {
    jvmToolchain(17)
}

sourceSets {
    main {
        kotlin.srcDir(generatedSourcesDir)
    }
}

tasks {
    generateKotlinExtensionsForScalaClasses {
        outputDirectory = file(generatedSourcesDir)
        // replace scala Seq params with arrays
        parameterMappings = listOf(
            ParameterMapping(
                originalType = Seq::class.asTypeName(),
                newTypeName = Array::class.asTypeName(),
                // use the MemberName in the template as opposed to a literal string so it gets imported automatically
                templatePrefix = "%M(",
                templatePrefixParams = listOf(CollectionConverters::class.member("asScala")),
                templateSuffix = ".iterator()).toSeq()",
            ),
        )
    }
}
