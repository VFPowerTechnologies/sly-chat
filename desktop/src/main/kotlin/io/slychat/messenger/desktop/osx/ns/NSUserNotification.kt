package io.slychat.messenger.desktop.osx.ns

import ca.weblite.objc.Client
import ca.weblite.objc.Peerable
import ca.weblite.objc.Proxy

class NSUserNotification(private val proxy: Proxy) : Peerable by proxy {
    companion object {
        const val DEFAULT_SOUND_NAME = "DefaultSoundName"

        const val ActivationTypeNone = 0
        const val ActivationTypeContentsClicked = 1
        const val ActivationTypeReplied = 3
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

    val activationType: Int
        get() = proxy.sendInt("activationType")

    var hasActionButton: Boolean
        get() = proxy.sendBoolean("hasActionButton")
        set(value) {
            proxy.send("setHasActionButton:", value.toObjc())
        }

    var hasReplyButton: Boolean
        get() = proxy.sendBoolean("hasReplyButton")
        set(value) {
            proxy.send("setHasReplyButton:", value.toObjc())
        }

    val response: NSAttributedString?
        get() {
            val response = proxy.sendProxy("response") ?: return null
            return NSAttributedString(response)
        }

    var responsePlaceholder: String?
        get() = getString(proxy.send("responsePlaceholder"))
        set(value) {
            proxy.send("setResponsePlaceholder:", value)
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