package io.slychat.messenger.core.http.api.contacts

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * @property version The new address book version, or -1 if the version given in the request didn't match the remote version.
 */
data class UpdateAddressBookResponse(
    @JsonProperty("version")
    val version: Int
)