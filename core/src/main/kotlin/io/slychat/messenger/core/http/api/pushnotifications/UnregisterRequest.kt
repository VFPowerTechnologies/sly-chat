package io.slychat.messenger.core.http.api.pushnotifications

import io.slychat.messenger.core.SlyAddress

data class UnregisterRequest(
    val address: SlyAddress,
    val unregistrationToken: String
)