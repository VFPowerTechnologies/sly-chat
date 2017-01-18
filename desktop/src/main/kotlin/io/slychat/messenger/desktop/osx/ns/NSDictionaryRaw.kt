package io.slychat.messenger.desktop.osx.ns

import ca.weblite.objc.Peerable
import ca.weblite.objc.Proxy

class NSDictionaryRaw(private val proxy: Proxy) : Peerable by proxy {
    operator fun get(key: String): Any? {
        return proxy.send("objectForKey:", key)
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