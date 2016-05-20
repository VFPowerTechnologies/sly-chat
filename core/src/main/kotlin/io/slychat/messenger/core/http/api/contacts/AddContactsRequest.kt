package io.slychat.messenger.core.http.api.contacts

data class AddContactsRequest(
    val contacts: List<RemoteContactEntry>
)