package io.slychat.messenger.services

import io.slychat.messenger.core.PlatformContact
import nl.komponents.kovenant.Promise
import rx.Observable

/** Interface for accessing a platform's contact data. */
interface PlatformContacts {
    /** Emitted whenever contacts to the local contacts database are detected. Must be bound to the main thread scheduler. */
    val contactsUpdated: Observable<Unit>

    fun fetchContacts(): Promise<List<PlatformContact>, Exception>
}