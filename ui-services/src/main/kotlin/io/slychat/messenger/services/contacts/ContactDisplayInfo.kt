package io.slychat.messenger.services.contacts

import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.ContactInfo

/** Minimum information about a contact for use in displays (notifications, etc). */
data class ContactDisplayInfo(
    val id: UserId,
    val email: String,
    val name: String
)

fun ContactInfo.toContactDisplayInfo(): ContactDisplayInfo =
    ContactDisplayInfo(id, email, name)