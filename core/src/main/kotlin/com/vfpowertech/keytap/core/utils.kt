package com.vfpowertech.keytap.core

import com.fasterxml.jackson.core.type.TypeReference

/**
 * Throw an IllegalArgumentException with the given message if the predicate is false.
 *
 * Mimics Scala's require().
 */
inline fun require(predicate: Boolean, message: String) {
    if (!predicate)
        throw IllegalArgumentException(message)
}

/** Shorthand for creating Jackson TypeRefrences */
inline fun <reified T> typeRef(): TypeReference<T> = object : TypeReference<T>() {}
