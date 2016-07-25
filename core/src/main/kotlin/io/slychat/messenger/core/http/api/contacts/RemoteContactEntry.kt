package io.slychat.messenger.core.http.api.contacts

import com.fasterxml.jackson.annotation.JsonProperty
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.AllowedMessageLevel

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
)