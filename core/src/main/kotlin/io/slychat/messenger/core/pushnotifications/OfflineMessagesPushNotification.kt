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
}

