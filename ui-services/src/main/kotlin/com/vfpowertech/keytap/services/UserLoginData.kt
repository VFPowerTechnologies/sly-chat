package com.vfpowertech.keytap.services

import com.vfpowertech.keytap.core.KeyTapAddress
import com.vfpowertech.keytap.core.UserId
import com.vfpowertech.keytap.core.crypto.KeyVault
import java.util.concurrent.atomic.AtomicReference

class UserLoginData(
    val address: KeyTapAddress,
    val keyVault: KeyVault,
    authToken: String?
) {
    val userId: UserId
        get() = address.id

    private val atomicRef = AtomicReference<String?>()
    var authToken: String?
        get() = atomicRef.get()

        set(value) {
            atomicRef.set(value)
        }

    init {
        this.authToken = authToken
    }
}

