package io.slychat.messenger.core.pushnotifications

import io.slychat.messenger.core.SlyAddress

data class OfflineMessagesPushNotification(
    val account: SlyAddress,
    val accountName: String,
    val info: List<OfflineMessageInfo>
) {
    companion object {
        const val TYPE = "offline-message"
    }

    fun getNotificationTitle(): String {
        return if (info.size == 1) {
            "New messages from ${info[0].name}"
        }
        else {
            val totalMessages = info.fold(0) { z, b ->
                z + b.pendingCount
            }
            "$totalMessages new messages"
        }
    }

    fun getNotificationText(): String {
        return if (info.size == 1) {
            val pendingCount = info[0].pendingCount
            val s = "$pendingCount new message"
            if (pendingCount > 1)
                s + "s"
            else
                s
        }
        else {
            "New messages for $accountName"
        }
    }
}

