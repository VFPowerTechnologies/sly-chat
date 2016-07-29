package io.slychat.messenger.core.persistence

import com.fasterxml.jackson.annotation.JsonProperty
import java.util.*

/**
 * @property encryptedData An encrypted, serialized AddressBookUpdate
 */
class RemoteAddressBookEntry(
    @JsonProperty("hash")
    val hash: String,
    @JsonProperty("encryptedData")
    val encryptedData: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false

        other as RemoteAddressBookEntry

        if (hash != other.hash) return false
        if (!Arrays.equals(encryptedData, other.encryptedData)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = hash.hashCode()
        result = 31 * result + Arrays.hashCode(encryptedData)
        return result
    }

    override fun toString(): String{
        return "RemoteAddressBookEntry(hash='$hash', encryptedData=${encryptedData.size}b)"
    }
}