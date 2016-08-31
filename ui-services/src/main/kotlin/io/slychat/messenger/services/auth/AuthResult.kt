package io.slychat.messenger.services.auth

import io.slychat.messenger.core.crypto.KeyVault
import io.slychat.messenger.core.http.api.authentication.DeviceInfo
import io.slychat.messenger.core.persistence.AccountInfo
import io.slychat.messenger.core.persistence.SessionData

data class AuthResult(
    val sessionData: SessionData,
    val keyVault: KeyVault,
    val accountInfo: AccountInfo,
    val otherDevices: List<DeviceInfo>?
)