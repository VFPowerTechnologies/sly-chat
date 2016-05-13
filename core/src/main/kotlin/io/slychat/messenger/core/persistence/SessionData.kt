package io.slychat.messenger.core.persistence

import io.slychat.messenger.core.AuthToken
import io.slychat.messenger.core.crypto.EncryptionSpec
import io.slychat.messenger.core.crypto.ciphers.CipherParams
import io.slychat.messenger.core.crypto.encryptDataWithParams
import io.slychat.messenger.core.crypto.hexify

data class SessionData(
    val authToken: AuthToken
) {
    fun serialize(localDataEncryptionKey: ByteArray, localDataEncryptionParams: CipherParams): SerializedSessionData {
        val encryptedAuthToken = encryptDataWithParams(EncryptionSpec(localDataEncryptionKey, localDataEncryptionParams), authToken.string.toByteArray(Charsets.UTF_8))
        return SerializedSessionData(encryptedAuthToken.data.hexify())
    }
}