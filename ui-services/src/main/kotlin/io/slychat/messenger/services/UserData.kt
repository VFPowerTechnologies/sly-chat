package io.slychat.messenger.services

import io.slychat.messenger.core.SlyAddress
import io.slychat.messenger.core.UserId

class UserData(
    val address: SlyAddress,
    val remotePasswordHash: ByteArray
) {
    val userId: UserId
        get() = address.id
}

