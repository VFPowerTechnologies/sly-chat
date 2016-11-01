package io.slychat.messenger.core.persistence

import io.slychat.messenger.core.UserId

data class ContactInfo(
    val id: UserId,
    val email: String,
    val name: String,
    val allowedMessageLevel: AllowedMessageLevel,
    val publicKey: String
)