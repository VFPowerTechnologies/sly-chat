package io.slychat.messenger.core.http.api.contacts

import com.fasterxml.jackson.annotation.JsonProperty

/** Takes a list of email hashes to remove. */
data class RemoveContactsRequest(
    val contacts: List<String>
)