# Scala Kotlin Extension Codegen

A library and gradle plugin for generating kotlin extension function wrappers around scala libraries with extensions

## Structure

The project is split into two main modules:

1. `codegen` - The library that generates the Kotlin extension functions given some configuration
2. `plugin-build` - The Gradle plugin that uses the `codegen` library to generate Kotlin extension wrapper functions for Scala libraries

## Usage

### Gradle Plugin

Add the plugin to your `build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.kotlin)
    id("com.stackgen.codegen.scalakotlin")
}
```

...and configure the `generateKotlinExtensionsForScalaClasses` task:
```kotlin
tasks {
    generateKotlinExtensionsForScalaClasses {
        outputDirectory = file(generatedSourcesDir)
    }
}
```

#### Repository

The plugin is not yet available on the Gradle Plugin Portal/Maven central. It is currently only available in the [Maven GitHub Package Registry](https://github.com/orgs/stackgenhq/packages?repo_name=scala-kotlin-extension-codegen).
Github Packages' Maven registry [requires authentication](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-apache-maven-registry#authenticating-to-github-packages), so it is recommended to generate a Personal Access Token with `packages:read` scope.

Add the following repository to your `settings.gradle.kts`:
```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()

        maven("https://maven.pkg.github.com/stackgenhq/scala-kotlin-extension-codegen") {
            name = "Scala-Kotlin Extension Codegen GPR"
            credentials {
                username = providers.gradleProperty("gpr.user").getOrElse(System.getenv("GPR_USER"))
                password = providers.gradleProperty("gpr.key").getOrElse(System.getenv("GPR_KEY"))
            }
        }
    }
}
```
...and add those keys to your `gradle.properties` or environment. See [Configuring the Build Environment](https://docs.gradle.org/current/userguide/build_environment.html#priority_for_configurations) for more on configuring Gradle properties.

#### Generate for Scala libraries/Jars

The plugin creates a `codegen` configuration, where scala dependencies can be added for the plugin to scan for extensions in order to generate wrapper kotlin extension functions.

Note that for the generated code to be compilable (as it calls into the scala libraries), the classes must be available on the compilation classpath.
If these generated wrappers are to be used in the same project, you can either add the dependencies to the necessary configurations manually or extend them from `codegen`, for example:
`build.gradle.kts`
```kotlin
// codegen deps must be available on classpath at runtime for the classloader, at compile time for the generated code
configurations.implementation.extendsFrom(configurations.codegen)
```

Then, simply add `codegen` dependencies:
```kotlin
dependencies {
    codegen("com.outr:scribe_3:3.15.0")
}
```

#### Generate for Scala classes

The task can be configured to scan a directory of Scala classes for extensions in order to generate wrapper kotlin extension functions. For example:
```kotlin
generateKotlinExtensionsForScalaClasses {
    classesDir = file("build/classes/scala/main")
    // ...other configuration
}
```

If these classes have library dependencies required for the classes to be loaded, they can be added via the `codegenClasspath` configuration:
```kotlin
dependencies {
    codegenClasspath("com.outr:scribe_3:3.15.0")
}
```

#### Advanced Configurations

To assist with the complexity of interoperability between Kotlin and Scala's type systems, the task also has configurations for remapping type variables and conversion of parameter types.

For example, [`scribe` has a `StringContext` extension function called `formatter`](https://github.com/outr/scribe/blob/3.15.3/core/shared/src/main/scala/scribe/format/package.scala#L208)
which accepts varargs of `Any`. This parameter becomes a `Seq` when compiled which is cumbersome to use from Kotlin. The generated extension would be:
```kotlin
  public fun StringContext.formatter(args: Seq<Any>): Formatter =
      `package`.FormatterInterpolator.`formatter$extension`(this, args)
```

`Seq` is not idiomatic in Kotlin, and we would prefer to use some Kotlin or Java collection instead.
We can remap this to an `Array` instead by setting the `parameterMappings` configuration in the task:
```kotlin
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
```
The templates follow the [KotlinPoet template format](https://square.github.io/kotlinpoet/).

This can be broken down as follows:
- `originalType` is the type to be replaced, here `Seq`
- `newTypeName` is the new type to replace it with, here `Array`
- `templatePrefix` is the KotlinPoet template to use ahead of any `Seq` method parameter in the wrapper function (a prefix to the `args` parameter in our example)
- `templatePrefixParams` is a list of parameters to pass to the prefix KotlinPoet template. We use the `%M` and this parameter to ensure the `asScala` method is imported.
- `templateSuffix` is the KotlinPoet template to use after any `Seq` method parameter in the wrapper function (a suffix to the `args` parameter in our example)
- `templateSuffixParams` is a list of parameters to pass to the suffix KotlinPoet template. We have no parameters to pass to the template here, so it's been omitted.

Finally, the codegen library will automatically use `varargs` for the last Array parameter in the generated extension function.

This will generate the following extension function:
```kotlin
import scala.jdk.javaapi.CollectionConverters.asScala
// ...
  public fun StringContext.formatter(vararg args: Any): Formatter =
      `package`.FormatterInterpolator.`formatter$extension`(this, asScala(args.iterator()).toSeq())
```
...and so we've substituted the `args: Seq<Any>` parameter with `vararg args: Any` from mapping the type, and `args` with `asScala(args.iterator()).toSeq()`
when calling the wrapped function via our templates, improving the ergonomics of using the generated extension function.

See also [example](example/build.gradle.kts)

## Contributing

Please read [CONTRIBUTING.md](.github/CONTRIBUTING.md) for details on contributing and our code of conduct.
