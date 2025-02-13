package com.stackgen.codegen

import com.squareup.kotlinpoet.asTypeName
import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.KTypeParameter

/**
 * Container for information about a scala extension required to generate a kotlin wrapper extension function
 *
 * Note: We use kotlin-reflect here rather than java reflection so we automatically get appropriate kotlin types (EG `Any` instead of `Object`)
 */
data class ExtensionFunction(
    val name: String,
    val typeParameters: List<KTypeParameter>,
    val extensionType: KType,
    val returnType: KType,
    val arguments: Collection<KParameter>,
) : Comparable<ExtensionFunction> {
    override fun compareTo(other: ExtensionFunction): Int =
        compareValuesBy(
            this,
            other,
            { it.extensionType.asTypeName().toString() },
            { it.name },
            { it.arguments.joinToString { a -> a.type.asTypeName().toString() } },
        )
}
