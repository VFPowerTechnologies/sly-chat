package io.slychat.messenger.core.http.api.contacts

data class UpdateContactsRequest(
    val contacts: List<RemoteContactEntry>
)