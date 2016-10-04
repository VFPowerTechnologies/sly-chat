package io.slychat.messenger.core.persistence

import com.fasterxml.jackson.annotation.JsonProperty
import io.slychat.messenger.core.hexify
import java.util.*

/**
 * @property encryptedData An encrypted, serialized AddressBookUpdate
 */
class RemoteAddressBookEntry(
    @JsonProperty("hash") val idHash: ByteArray,
    @JsonProperty("dataHash") val dataHash: ByteArray,
    @JsonProperty("encryptedData") val encryptedData: ByteArray
) {
    override fun toString(): String {
        return "RemoteAddressBookEntry(hash='${idHash.hexify()}', encryptedData=[${encryptedData.size}b], dataHash='${dataHash.hexify()}')"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false

        other as RemoteAddressBookEntry

        if (!Arrays.equals(idHash, other.idHash)) return false
        if (!Arrays.equals(encryptedData, other.encryptedData)) return false
        if (!Arrays.equals(dataHash, other.dataHash)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = Arrays.hashCode(idHash)
        result = 31 * result + Arrays.hashCode(encryptedData)
        result = 31 * result + Arrays.hashCode(dataHash)
        return result
    }
}
