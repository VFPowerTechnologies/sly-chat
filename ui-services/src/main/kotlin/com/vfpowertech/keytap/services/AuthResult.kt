package com.vfpowertech.keytap.services

import com.vfpowertech.keytap.core.crypto.KeyVault

data class AuthResult(val authToken: String, val keyRegenCount: Int, val keyVault: KeyVault)