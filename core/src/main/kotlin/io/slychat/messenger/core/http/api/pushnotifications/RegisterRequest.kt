package io.slychat.messenger.core.http.api.pushnotifications

data class RegisterRequest(
    val token: String,
    val service: PushNotificationService,
    val isAudio: Boolean
)

