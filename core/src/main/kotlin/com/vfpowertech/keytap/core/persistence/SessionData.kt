package com.vfpowertech.keytap.core.persistence

import com.vfpowertech.keytap.core.crypto.ciphers.CipherParams
import com.vfpowertech.keytap.core.crypto.encryptDataWithParams
import com.vfpowertech.keytap.core.crypto.hexify
import javax.crypto.spec.SecretKeySpec

data class SessionData(
    val authToken: String
) {
    fun serialize(localDataEncryptionKey: ByteArray, localDataEncryptionParams: CipherParams): SerializedSessionData {
        val key = SecretKeySpec(localDataEncryptionKey, localDataEncryptionParams.keyType)

        val encryptedAuthToken = encryptDataWithParams(key, authToken.toByteArray(Charsets.UTF_8), localDataEncryptionParams)
        return SerializedSessionData(encryptedAuthToken.data.hexify())
    }
}