package io.slychat.messenger.core.http.api.contacts

/** Takes a list of email hashes to remove. */
data class RemoveContactsRequest(
    val contacts: List<String>
)