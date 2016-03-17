package com.vfpowertech.keytap.core.persistence

data class ContactListDiff(
    val newContacts: Set<String>,
    val removedContacts: Set<String>
)