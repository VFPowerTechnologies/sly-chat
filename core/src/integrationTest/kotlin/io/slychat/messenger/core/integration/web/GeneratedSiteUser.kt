package io.slychat.messenger.core.integration.web

import io.slychat.messenger.core.crypto.KeyVault

class GeneratedSiteUser(
    val user: SiteUser,
    val keyVault: KeyVault,
    val remotePasswordHash: ByteArray
)