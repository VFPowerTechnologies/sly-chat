package io.slychat.messenger.core.http.api.contacts

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * @property hash The new address book hash.
 * @property updated Whether or not the client's pushed entries caused an update on the server.
 */
data class UpdateAddressBookResponse(
    @JsonProperty("hash")
    val hash: String,
    @JsonProperty("updated")
    val updated: Boolean
)