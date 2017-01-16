package io.slychat.messenger.desktop.osx.ns

import ca.weblite.objc.Client
import ca.weblite.objc.Peerable
import ca.weblite.objc.Proxy

class NSDate(private val proxy: Proxy) : Peerable by proxy {
    companion object {
        @JvmStatic
        fun date(): NSDate {
            return NSDate(Client.getInstance().sendProxy("NSDate", "date"))
        }
    }

    operator fun plus(timeInterval: Double): NSDate {
        return NSDate(proxy.sendProxy("dateByAddingTimeInterval:", timeInterval))
    }
}