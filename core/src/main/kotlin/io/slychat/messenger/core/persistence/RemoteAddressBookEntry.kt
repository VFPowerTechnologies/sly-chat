package io.slychat.messenger.core.persistence

import com.fasterxml.jackson.annotation.JsonProperty
import java.util.*

/**
 * @property encryptedData An encrypted, serialized AddressBookUpdate
 */
class RemoteAddressBookEntry(
    @JsonProperty("idHash") val idHash: String,
    @JsonProperty("dataHash") val dataHash: String,
    @JsonProperty("encryptedData") val encryptedData: ByteArray
) {
    override fun toString(): String {
        return "RemoteAddressBookEntry(hash='$idHash', encryptedData=[${encryptedData.size}b], dataHash='$dataHash')"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false

        other as RemoteAddressBookEntry

        if (idHash != other.idHash) return false
        if (dataHash != other.dataHash) return false
        if (!Arrays.equals(encryptedData, other.encryptedData)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = idHash.hashCode()
        result = 31 * result + dataHash.hashCode()
        result = 31 * result + Arrays.hashCode(encryptedData)
        return result
    }

}
