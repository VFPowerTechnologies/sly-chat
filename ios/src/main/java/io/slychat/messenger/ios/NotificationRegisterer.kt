package io.slychat.messenger.ios

import nl.komponents.kovenant.Promise

//can't think of a better name
interface NotificationRegisterer {
    fun registerForNotifications(): Promise<String?, Exception>
}