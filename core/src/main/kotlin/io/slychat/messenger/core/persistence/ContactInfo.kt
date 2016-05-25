package io.slychat.messenger.core.persistence

import io.slychat.messenger.core.UserId

data class ContactInfo(
    val id: UserId,
    val email: String,
    val name: String,
    val isPending: Boolean,
    val phoneNumber: String?,
    val publicKey: String
)