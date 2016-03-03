package com.vfpowertech.keytap.core.persistence

import com.fasterxml.jackson.annotation.JsonProperty
import com.vfpowertech.keytap.core.crypto.ciphers.CipherParams
import com.vfpowertech.keytap.core.crypto.decryptData
import com.vfpowertech.keytap.core.crypto.unhexify
import javax.crypto.spec.SecretKeySpec

data class SerializedSessionData(
    @param:JsonProperty("encryptedAuthToken")
    val encryptedAuthToken: String
) {
    fun deserialize(localDataEncryptionKey: ByteArray, localDataEncryptionParams: CipherParams): SessionData {
        val key = SecretKeySpec(localDataEncryptionKey, localDataEncryptionParams.keyType)

        val authToken = decryptData(key, encryptedAuthToken.unhexify(), localDataEncryptionParams).toString(Charsets.UTF_8)

        return SessionData(authToken)
    }
}