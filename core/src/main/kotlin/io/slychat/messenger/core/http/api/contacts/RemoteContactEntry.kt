package io.slychat.messenger.core.http.api.contacts

import com.fasterxml.jackson.annotation.JsonProperty
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.AllowedMessageLevel
import java.util.*

/** Represents a saved remote contact entry. */
data class RemoteContactEntryData(
    @JsonProperty("userId")
    val userId: UserId,
    @JsonProperty("allowedMessageLevel")
    val allowedMessageLevel: AllowedMessageLevel
)

/** Encrypted [RemoteContactEntryData] and user ID hash uploaded to remote server. */
class RemoteContactEntry(
    @JsonProperty("hash")
    val hash: String,
    @JsonProperty("encryptedContactData")
    val encryptedContactData: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false

        other as RemoteContactEntry

        if (hash != other.hash) return false
        if (!Arrays.equals(encryptedContactData, other.encryptedContactData)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = hash.hashCode()
        result = 31 * result + Arrays.hashCode(encryptedContactData)
        return result
    }

    override fun toString(): String {
        return "RemoteContactEntry(hash='$hash', encryptedContactData=[${encryptedContactData.size}b])"
    }
}