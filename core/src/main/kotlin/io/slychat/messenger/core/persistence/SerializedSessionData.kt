package io.slychat.messenger.core.persistence

import com.fasterxml.jackson.annotation.JsonProperty
import io.slychat.messenger.core.AuthToken
import io.slychat.messenger.core.crypto.EncryptionSpec
import io.slychat.messenger.core.crypto.ciphers.CipherParams
import io.slychat.messenger.core.crypto.decryptData
import io.slychat.messenger.core.crypto.unhexify

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