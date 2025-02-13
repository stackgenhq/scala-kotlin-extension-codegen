package com.stackgen.codegen

import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.core.spec.style.BehaviorSpec
import java.io.File

class ScalaExtensionFinderTest : BehaviorSpec({
    Given("scribe") {
        val scribeJar =
            File(javaClass.classLoader.loadClass("scribe.Logger").protectionDomain.codeSource.location.toURI())

        When("Scan the scribe jar") {
            val findExtensions = { ScalaExtensionFinder(ClassLoader.getSystemClassLoader()).scanJar(scribeJar) }
            Then("No exception is thrown") {
                shouldNotThrow<NullPointerException>(findExtensions)
            }
        }
    }
})
