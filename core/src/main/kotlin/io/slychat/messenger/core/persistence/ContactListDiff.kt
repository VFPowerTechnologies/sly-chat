package io.slychat.messenger.core.persistence

import io.slychat.messenger.core.UserId

data class ContactListDiff(
    val newContacts: Set<UserId>,
    val removedContacts: Set<UserId>
)