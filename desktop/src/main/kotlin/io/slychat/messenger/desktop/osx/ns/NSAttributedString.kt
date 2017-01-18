package io.slychat.messenger.desktop.osx.ns

import ca.weblite.objc.Peerable
import ca.weblite.objc.Proxy

class NSAttributedString(private val proxy: Proxy) : Peerable by proxy {
    val string: String
        get() = getString(proxy.send("string"))!!

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