package com.vfpowertech.keytap.services

import com.vfpowertech.keytap.core.AuthToken
import com.vfpowertech.keytap.core.crypto.KeyVault
import com.vfpowertech.keytap.core.persistence.AccountInfo

data class AuthResult(
    val authToken: AuthToken?,
    val keyVault: KeyVault,
    val accountInfo: AccountInfo
)