package io.slychat.messenger.core.http.api.contacts

data class UpdateContactsRequest(
    val add: List<RemoteContactEntry>,
    val remove: List<String>
)