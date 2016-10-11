package io.slychat.messenger.services.contacts

import io.slychat.messenger.core.persistence.ContactInfo

data class ContactUpdate(
    val old: ContactInfo,
    val new: ContactInfo
)