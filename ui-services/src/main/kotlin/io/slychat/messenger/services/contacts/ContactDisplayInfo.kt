package io.slychat.messenger.services.contacts

import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.ContactInfo
import io.slychat.messenger.core.persistence.GroupId

/** Minimum information about a contact for use in displays (notifications, etc). */
data class ContactDisplayInfo(
    val id: UserId,
    val name: String,
    val groupId: GroupId?,
    val groupName: String?
) {
    init {
        if (groupId != null && groupName == null)
            throw IllegalArgumentException("groupId is non-null but groupName is null")

        if (groupName != null && groupId == null)
            throw IllegalArgumentException("groupName is non-null but groupId is null")
    }
}

fun ContactInfo.toContactDisplayInfo(): ContactDisplayInfo =
    ContactDisplayInfo(id, name, null, null)