package com.vfpowertech.keytap.core.persistence

import com.vfpowertech.keytap.core.UserId

data class ContactListDiff(
    val newContacts: Set<UserId>,
    val removedContacts: Set<UserId>
)