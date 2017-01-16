package io.slychat.messenger.desktop.osx.ns

import ca.weblite.objc.Client
import ca.weblite.objc.NSObject
import ca.weblite.objc.Proxy
import com.sun.jna.Pointer

class NSAppleEventManager(private val proxy: Proxy) {
    companion object {
        //AppleEvents.h
        //'aevt'
        const val kCoreEventClass: Int = 0x61657674

        //AERegistry.h
        //'rapp'
        const val kAEReopenApplication: Int = 0x72617070

        @JvmStatic
        val sharedAppleEventManager: NSAppleEventManager
            get() {
                val client = Client.getInstance()

                return NSAppleEventManager(client.sendProxy("NSAppleEventManager", "sharedAppleEventManager"))
            }
    }

    fun setEventHandler(handler: NSObject, selector: Pointer, eventClass: Int, eventID: Int) {
        proxy.send("setEventHandler:andSelector:forEventClass:andEventID:", handler, selector, eventClass, eventID)
    }
}