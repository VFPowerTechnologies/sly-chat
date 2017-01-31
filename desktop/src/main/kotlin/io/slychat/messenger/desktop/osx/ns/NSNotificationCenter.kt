package io.slychat.messenger.desktop.osx.ns

import ca.weblite.objc.Client
import ca.weblite.objc.NSObject
import ca.weblite.objc.Peerable
import ca.weblite.objc.Proxy
import com.sun.jna.Pointer

class NSNotificationCenter(private val proxy: Proxy) : Peerable by proxy {
    companion object {
        @JvmStatic
        val defaultCenter: NSNotificationCenter
            get() {
                val client = Client.getInstance()

                return NSNotificationCenter(client.sendProxy("NSNotificationCenter", "defaultCenter"))
            }
    }

    fun addObserver(observer: NSObject, selector: Pointer, name: String?, sender: NSObject?) {
        proxy.send("addObserver:selector:name:object:", observer, selector, name, sender)
    }

    fun removeObserver(observer: NSObject, name: String?, sender: NSObject?) {
        proxy.send("removeObserver:name:object:", observer, name, sender)
    }
}