package io.slychat.messenger.desktop.osx.ns

import ca.weblite.objc.Client
import ca.weblite.objc.Proxy

class NSApplication(private val proxy: Proxy) {
    companion object {
        //https://developer.apple.com/reference/appkit/nsrequestuserattentiontype?language=objc
        const val NSCriticalRequest = 0
        const val NSInformationRequest = 10

        const val DidFinishLaunchingNotification = "NSApplicationDidFinishLaunchingNotification"

        @JvmStatic
        val sharedApplication: NSApplication
            get() {
                val client = Client.getInstance()

                return NSApplication(client.sendProxy("NSApplication", "sharedApplication"))
            }
    }

    val dockTile: NSDockTile
        get() = NSDockTile(proxy.sendProxy("dockTile"))

    fun requestUserAttention(requestType: Int) {
        proxy.sendInt("requestUserAttention:", requestType)
    }
}