package io.slychat.messenger.services

import io.slychat.messenger.core.SlyAddress
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.crypto.KeyVault

class UserData(
    val address: SlyAddress,
    val keyVault: KeyVault,
    val remotePasswordHash: ByteArray
) {
    val userId: UserId
        get() = address.id
}

