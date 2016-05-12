package io.slychat.messenger.services

import io.slychat.messenger.core.KeyTapAddress
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.crypto.KeyVault

class UserData(
    val address: KeyTapAddress,
    val keyVault: KeyVault
) {
    val userId: UserId
        get() = address.id
}

