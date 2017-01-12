package io.slychat.messenger.desktop.osx.ns

import ca.weblite.objc.Client
import ca.weblite.objc.Peerable
import ca.weblite.objc.Proxy

class NSMutableDictionary(private val proxy: Proxy) : Peerable by proxy {
    constructor() : this(Client.getInstance().sendProxy("NSMutableDictionary", "new"))

    operator fun get(key: String): String? {
        return getString(proxy.send("objectForKey:", key))
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