package io.slychat.messenger.services.auth

import io.slychat.messenger.core.crypto.KeyVault
import io.slychat.messenger.core.http.api.authentication.DeviceInfo
import io.slychat.messenger.core.persistence.AccountInfo
import io.slychat.messenger.core.persistence.AccountLocalInfo
import io.slychat.messenger.core.persistence.SessionData

class AuthResult(
    val sessionData: SessionData,
    val keyVault: KeyVault,
    val remotePasswordHash: ByteArray,
    val accountInfo: AccountInfo,
    val accountLocalInfo: AccountLocalInfo,
    val otherDevices: List<DeviceInfo>?
)