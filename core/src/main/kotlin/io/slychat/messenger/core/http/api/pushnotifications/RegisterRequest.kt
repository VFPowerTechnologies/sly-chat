package io.slychat.messenger.core.http.api.pushnotifications

data class RegisterRequest(
    val token: String,
    val audioToken: String,
    val service: PushNotificationService
)

