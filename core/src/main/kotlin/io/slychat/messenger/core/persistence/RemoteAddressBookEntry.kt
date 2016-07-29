package io.slychat.messenger.core.persistence

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * @property encryptedData An encrypted, serialized AddressBookUpdate
 */
class RemoteAddressBookEntry(
    @JsonProperty("hash")
    val hash: String,
    @JsonProperty("encryptedData")
    val encryptedData: ByteArray
)