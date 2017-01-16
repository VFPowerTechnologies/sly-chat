package io.slychat.messenger.desktop.osx.ns

import ca.weblite.objc.Client
import ca.weblite.objc.Proxy

class NSUserNotificationCenter(private val proxy: Proxy) {
    companion object {
        @JvmStatic
        val defaultUserNotificationCenter: NSUserNotificationCenter
            get() {
                val client = Client.getInstance()

                return NSUserNotificationCenter(client.sendProxy("NSUserNotificationCenter", "defaultUserNotificationCenter"))
            }
    }

    var delegate: NSUserNotificationCenterDelegate?
        //this isn't really possible without some work so ignoring it
        //doesn't seem to just work even though it has an objc counterpart
        //figured RuntimeUtils.getJavaPeer would work here
        get() = TODO()
        set(value) {
            proxy.send("setDelegate:", value)
        }

    fun deliverNotification(notification: NSUserNotification) {
        proxy.send("deliverNotification:", notification)
    }

    fun scheduleNotification(notification: NSUserNotification) {
        proxy.send("scheduleNotification:", notification)
    }

    fun removeAllDeliveredNotifications() {
        proxy.send("removeAllDeliveredNotifications")
    }

    fun removeDeliveredNotification(notification: NSUserNotification) {
        proxy.send("removeDeliveredNotification:", notification)
    }

    fun deliveredNotifications(): NSArrayNSUserNotification {
        return NSArrayNSUserNotification(proxy.send("deliveredNotifications") as Proxy)
    }
}