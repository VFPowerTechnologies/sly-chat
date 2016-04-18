package com.vfpowertech.keytap.services

import com.vfpowertech.keytap.core.UserId
import com.vfpowertech.keytap.core.crypto.KeyVault

class UserLoginData(
    val userId: UserId,
    val username: String,
    val keyVault: KeyVault,
    //may be null if refreshing (manipulated by the MessengerService)
    var authToken: String?
)