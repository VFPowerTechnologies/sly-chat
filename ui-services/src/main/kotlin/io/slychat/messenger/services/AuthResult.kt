package io.slychat.messenger.services

import io.slychat.messenger.core.AuthToken
import io.slychat.messenger.core.crypto.KeyVault
import io.slychat.messenger.core.persistence.AccountInfo

data class AuthResult(
    val authToken: AuthToken?,
    val keyVault: KeyVault,
    val accountInfo: AccountInfo
)