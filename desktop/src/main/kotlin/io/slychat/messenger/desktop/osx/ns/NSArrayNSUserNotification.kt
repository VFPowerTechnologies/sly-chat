package io.slychat.messenger.desktop.osx.ns

import ca.weblite.objc.Client
import ca.weblite.objc.Peerable
import ca.weblite.objc.Proxy

class NSArrayNSUserNotification(
    private val proxy: Proxy
): Peerable by proxy, Iterable<NSUserNotification> {
    private inner class Iter : Iterator<NSUserNotification> {
        private var i = 0

        override fun hasNext(): Boolean {
            return i < this@NSArrayNSUserNotification.count
        }

        override fun next(): NSUserNotification {
            val v = this@NSArrayNSUserNotification.objectAtIndex(i)
            i += 1
            return v
        }
    }

    constructor() : this(Client.getInstance().sendProxy("NSArray", "new"))

    //NSUInteger, so use long
    val count: Long
        get() {
            return proxy.send("count") as Long
        }

    fun objectAtIndex(index: Int): NSUserNotification {
        return NSUserNotification(proxy.send("objectAtIndex:", index) as Proxy)
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

    override fun iterator(): Iterator<NSUserNotification> {
        return Iter()
    }
}