package com.stackgen.codegen

import com.squareup.kotlinpoet.TypeName

/**
 * Mapping enabling retyping extension function parameters and providing template modification to convert it when
 * passing to the wrapped function
 */
data class ParameterMapping(
    val originalType: TypeName,
    val newTypeName: TypeName,
    val templatePrefix: String? = null,
    val templatePrefixParams: List<Any?>? = null,
    val templateSuffix: String? = null,
    val templateSuffixParams: List<Any?>? = null,
)
