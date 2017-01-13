package io.slychat.messenger.desktop.osx.ns

import ca.weblite.objc.Client
import ca.weblite.objc.Peerable
import ca.weblite.objc.Proxy

class NSUserNotification(private val proxy: Proxy) : Peerable by proxy {
    companion object {
        const val DEFAULT_SOUND_NAME = "DefaultSoundName"
    }

    constructor() : this(Client.getInstance().sendProxy("NSUserNotification", "new"))

    var title: String?
        get() = proxy.sendString("title")
        set(value) {
            proxy.send("setTitle:", value)
        }

    var informativeText: String?
        get() = proxy.sendString("informativeText")
        set(value) {
            proxy.send("setInformativeText:", value)
        }

    var deliveryDate: NSDate?
        get() = NSDate(proxy.sendProxy("deliveryDate"))
        set(value) {
            proxy.send("setDeliveryDate:", value)
        }

    var userInfo: NSDictionary//<String, out Any>
        get() {
            val dictProxy = proxy.sendProxy("userInfo")
            return NSDictionary(dictProxy)
        }
        set(value) {
            proxy.send("setUserInfo:", value)
        }

    val wasPresented: Boolean
        get() = proxy.sendBoolean("isPresented")

    var soundName: String?
        get() = proxy.sendString("soundName")
        set(value) {
            proxy.send("setSoundName:", value)
        }
}