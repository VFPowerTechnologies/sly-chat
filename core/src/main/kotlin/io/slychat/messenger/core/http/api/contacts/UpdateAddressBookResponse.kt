package io.slychat.messenger.core.http.api.contacts

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * @property hash The new address book hash.
 */
data class UpdateAddressBookResponse(
    @JsonProperty("hash")
    val hash: String
)