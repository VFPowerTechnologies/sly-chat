package io.slychat.messenger.desktop.osx.ns

import ca.weblite.objc.Peerable
import ca.weblite.objc.Proxy
import ca.weblite.objc.Runtime
import ca.weblite.objc.RuntimeUtils

class NSDictionary(private val proxy: Proxy) : Peerable by proxy {
    operator fun get(key: String): String? {
        val v = proxy.send("objectForKey:", key)

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

    override fun toString(): String {
        return proxy.toString()
    }

    override fun equals(other: Any?): Boolean {
        return proxy == other
    }

    override fun hashCode(): Int {
        return proxy.hashCode()
    }
}