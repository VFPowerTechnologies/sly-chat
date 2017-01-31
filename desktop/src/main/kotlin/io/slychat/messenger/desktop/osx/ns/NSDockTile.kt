package io.slychat.messenger.desktop.osx.ns

import ca.weblite.objc.Peerable
import ca.weblite.objc.Proxy

class NSDockTile(private val proxy: Proxy) : Peerable by proxy {
    var badgeLabel: String?
        get() = proxy.sendString("badgeLabel")
        set(value) {
            proxy.send("setBadgeLabel:", value)
        }
}