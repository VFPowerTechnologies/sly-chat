package com.vfpowertech.keytap.services

import com.vfpowertech.keytap.core.KeyTapAddress
import com.vfpowertech.keytap.core.UserId
import com.vfpowertech.keytap.core.crypto.KeyVault

class UserData(
    val address: KeyTapAddress,
    val keyVault: KeyVault
) {
    val userId: UserId
        get() = address.id
}

