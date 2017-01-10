package io.slychat.messenger.desktop.osx.ns

import ca.weblite.objc.Client
import ca.weblite.objc.Peerable
import ca.weblite.objc.Proxy
import ca.weblite.objc.Runtime
import ca.weblite.objc.RuntimeUtils

class NSMutableDictionary(private val proxy: Proxy) : Peerable by proxy {
    constructor() : this(Client.getInstance().sendProxy("NSMutableDictionary", "new"))

    operator fun get(key: String): String {
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

    operator fun set(key: String, value: String) {
        proxy.send("setObject:forKey:", value, key)
    }

    //hack for simplity
    fun toNSDictionary(): NSDictionary = NSDictionary(proxy)

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