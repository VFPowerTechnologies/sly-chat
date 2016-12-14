@file:JvmName("IOSUtils")
package io.slychat.messenger.ios

import apple.foundation.NSArray

/**
 * Utility for creating an NSArray from a variable set of objects.
 *
 * Handles appending the null terminate during construction, as well as casting to the expected type, as
 * arrayWithObjects ignores the given type and just returns an NSArray<*>.
 */
fun <T> nsarray(first: T, vararg items: T): NSArray<T> {
    @Suppress("UNCHECKED_CAST")
    return NSArray.arrayWithObjects(first, *items, null) as NSArray<T>
}
