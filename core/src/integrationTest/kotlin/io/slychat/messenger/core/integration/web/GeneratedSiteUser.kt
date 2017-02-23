package io.slychat.messenger.core.integration.web

import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.crypto.KeyVault

class GeneratedSiteUser(
    val user: SiteUser,
    val keyVault: KeyVault,
    val remotePasswordHash: ByteArray
) {
    val id: UserId
        get() = user.id
}