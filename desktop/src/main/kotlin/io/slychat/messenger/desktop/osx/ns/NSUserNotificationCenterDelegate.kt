package io.slychat.messenger.desktop.osx.ns

import ca.weblite.objc.NSObject
import ca.weblite.objc.Proxy
import ca.weblite.objc.annotations.Msg

abstract class NSUserNotificationCenterDelegate : NSObject() {
    init {
        init("NSObject")
    }

    //these need to be public otherwise they aren't called (protected/private doesn't work)
    //since internal isn't a real jvm access level we can use that

    @Msg(selector="userNotificationCenter:didDeliverNotification:", signature = "v@:@@")
    internal fun _didDeliverNotification(center: Proxy, notification: Proxy) {
        didDeliverNotification(NSUserNotificationCenter(center), NSUserNotification(notification))
    }

    @Msg(selector = "userNotificationCenter:didActivateNotification:", signature = "v@:@@")
    internal fun _didActivateNotification(center: Proxy, notification: Proxy) {
        didActivateNotification(NSUserNotificationCenter(center), NSUserNotification(notification))
    }

    @Msg(selector = "userNotificationCenter:shouldPresentNotification:", signature = "c@:@@")
    internal fun _shouldPresentNotification(center: Proxy, notification: Proxy): Byte {
        val b = shouldPresentNotification(NSUserNotificationCenter(center), NSUserNotification(notification))

        return b.toObjc()
    }

    abstract fun didDeliverNotification(center: NSUserNotificationCenter, notification: NSUserNotification)
    abstract fun didActivateNotification(center: NSUserNotificationCenter, notification: NSUserNotification)
    abstract fun shouldPresentNotification(center: NSUserNotificationCenter, notification: NSUserNotification): Boolean
}