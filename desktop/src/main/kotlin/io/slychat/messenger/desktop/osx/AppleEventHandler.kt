package io.slychat.messenger.desktop.osx

import ca.weblite.objc.NSObject
import ca.weblite.objc.Proxy
import ca.weblite.objc.annotations.Msg
import io.slychat.messenger.desktop.DesktopApp

class AppleEventHandler(private val app: DesktopApp) : NSObject() {
    init {
        init("NSObject")
    }

    @Suppress("unused")
    @Msg(selector = "handleAppleEvent:withReplyEvent:", signature = "v@:@@")
    fun handleAppleEvent(event: Proxy, replyEvent: Proxy) {
        app.onReopenEvent()
    }
}
