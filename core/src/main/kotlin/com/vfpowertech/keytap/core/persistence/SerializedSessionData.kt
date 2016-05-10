package com.vfpowertech.keytap.core.persistence

import com.fasterxml.jackson.annotation.JsonProperty
import com.vfpowertech.keytap.core.AuthToken
import com.vfpowertech.keytap.core.crypto.EncryptionSpec
import com.vfpowertech.keytap.core.crypto.ciphers.CipherParams
import com.vfpowertech.keytap.core.crypto.decryptData
import com.vfpowertech.keytap.core.crypto.unhexify

data class SerializedSessionData(
    @param:JsonProperty("encryptedAuthToken")
    val encryptedAuthToken: String
) {
    fun deserialize(localDataEncryptionKey: ByteArray, localDataEncryptionParams: CipherParams): SessionData {
        val key = localDataEncryptionKey

        val authToken = decryptData(EncryptionSpec(key, localDataEncryptionParams), encryptedAuthToken.unhexify()).toString(Charsets.UTF_8)

        return SessionData(AuthToken(authToken))
    }
}