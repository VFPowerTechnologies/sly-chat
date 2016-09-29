package io.slychat.messenger.core.crypto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import io.slychat.messenger.core.crypto.hashes.HashParams
import java.util.*

@JsonIgnoreProperties(ignoreUnknown = true)
class SerializedKeyVault(
    @JsonProperty("encryptedKeyPair")
    val encryptedKeyPair: ByteArray,

    @JsonProperty("encryptedMasterKey")
    val encryptedMasterKey: ByteArray,

    @JsonProperty("encryptedAnonymizingData")
    val encryptedAnonymizingData: ByteArray,

    @JsonProperty("localPasswordHashParams")
    val localPasswordHashParams: HashParams
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false

        other as SerializedKeyVault

        if (!Arrays.equals(encryptedKeyPair, other.encryptedKeyPair)) return false
        if (!Arrays.equals(encryptedMasterKey, other.encryptedMasterKey)) return false
        if (!Arrays.equals(encryptedAnonymizingData, other.encryptedAnonymizingData)) return false
        if (localPasswordHashParams != other.localPasswordHashParams) return false

        return true
    }

    override fun hashCode(): Int {
        var result = Arrays.hashCode(encryptedKeyPair)
        result = 31 * result + Arrays.hashCode(encryptedMasterKey)
        result = 31 * result + Arrays.hashCode(encryptedAnonymizingData)
        result = 31 * result + localPasswordHashParams.hashCode()
        return result
    }
}