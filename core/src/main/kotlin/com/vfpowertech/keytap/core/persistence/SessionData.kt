package com.vfpowertech.keytap.core.persistence

import com.vfpowertech.keytap.core.crypto.EncryptionSpec
import com.vfpowertech.keytap.core.crypto.ciphers.CipherParams
import com.vfpowertech.keytap.core.crypto.encryptDataWithParams
import com.vfpowertech.keytap.core.crypto.hexify

data class SessionData(
    val authToken: String
) {
    fun serialize(localDataEncryptionKey: ByteArray, localDataEncryptionParams: CipherParams): SerializedSessionData {
        val encryptedAuthToken = encryptDataWithParams(EncryptionSpec(localDataEncryptionKey, localDataEncryptionParams), authToken.toByteArray(Charsets.UTF_8))
        return SerializedSessionData(encryptedAuthToken.data.hexify())
    }
}