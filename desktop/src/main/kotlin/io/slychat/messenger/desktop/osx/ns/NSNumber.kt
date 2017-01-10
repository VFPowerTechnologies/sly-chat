package io.slychat.messenger.desktop.osx.ns

import ca.weblite.objc.Client
import ca.weblite.objc.Peerable
import ca.weblite.objc.Proxy

class NSNumber(private val proxy: Proxy) : Peerable by proxy {
    companion object {
        fun withBool(b: Boolean): NSNumber {
            return NSNumber(Client.getInstance().sendProxy("NSNumber", "numberWithBool:", b.toObjc()))
        }

        fun withInt(i: Int): NSNumber {
            return NSNumber(Client.getInstance().sendProxy("NSNumber", "numberWithInt:", i))
        }
    }
}
