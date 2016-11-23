package io.slychat.messenger.ios

import io.slychat.messenger.core.PlatformContact
import io.slychat.messenger.services.PlatformContacts
import nl.komponents.kovenant.Promise
import rx.Observable
import rx.subjects.PublishSubject

class IOSPlatformContacts : PlatformContacts {
    private val contactsUpdateSubject = PublishSubject.create<Unit>()

    override val contactsUpdated: Observable<Unit>
        get() = contactsUpdateSubject

    override fun fetchContacts(): Promise<List<PlatformContact>, Exception> {
        return Promise.ofSuccess(emptyList())
    }
}