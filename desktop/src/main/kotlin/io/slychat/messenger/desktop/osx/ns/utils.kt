@file:JvmName("NSUtils")
package io.slychat.messenger.desktop.osx.ns

import ca.weblite.objc.Proxy
import ca.weblite.objc.Runtime
import ca.weblite.objc.RuntimeUtils

fun getString(v: Any?): String? {
    if (v == null)
        return null

    return when (v) {
        is String -> v
        is Proxy -> {
            val className = Runtime.INSTANCE.object_getClassName(v.peer)

            if (className == "__NSCFConstantString")
                RuntimeUtils.str(v.peer)
            else
                throw RuntimeException("Unable to convert $className to string")
        }
        else -> throw RuntimeException("Unexpected return type: ${v.javaClass}")
    }
}

fun Boolean.toObjc(): Byte {
    return if (this) 1 else 0
}
