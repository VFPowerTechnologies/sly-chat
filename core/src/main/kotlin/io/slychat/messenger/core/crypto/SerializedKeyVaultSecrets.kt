package io.slychat.messenger.core.crypto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import io.slychat.messenger.core.crypto.ciphers.Key
import java.util.*

@JsonIgnoreProperties(ignoreUnknown = true)
class SerializedKeyVaultSecrets(
    @JsonProperty("serializedIdentityKeyPair")
    val serializedIdentityKeyPair: ByteArray,

    @JsonProperty("masterKey")
    val masterKey: Key,

    @JsonProperty("anonymizingData")
    val anonymizingData: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false

        other as SerializedKeyVaultSecrets

        if (!Arrays.equals(serializedIdentityKeyPair, other.serializedIdentityKeyPair)) return false
        if (masterKey != other.masterKey) return false
        if (!Arrays.equals(anonymizingData, other.anonymizingData)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = Arrays.hashCode(serializedIdentityKeyPair)
        result = 31 * result + masterKey.hashCode()
        result = 31 * result + Arrays.hashCode(anonymizingData)
        return result
    }
}