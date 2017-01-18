package io.slychat.messenger.desktop.osx.ns

import ca.weblite.objc.Peerable
import ca.weblite.objc.Proxy
import io.slychat.messenger.desktop.osx.ns.NSDictionaryRaw
import io.slychat.messenger.desktop.osx.ns.getString

class NSNotification(private val proxy: Proxy) : Peerable by proxy {
    val name: String
        get() = getString(proxy.send("name"))!!

    var userInfo: NSDictionaryRaw?//<String, out Any>
        get() {
            val dictProxy = proxy.sendProxy("userInfo")
            return if (dictProxy != null) NSDictionaryRaw(dictProxy) else null
        }
        set(value) {
            proxy.send("setUserInfo:", value)
        }
}