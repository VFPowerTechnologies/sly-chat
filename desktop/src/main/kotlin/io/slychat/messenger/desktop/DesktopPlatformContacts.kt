package io.slychat.messenger.desktop

import io.slychat.messenger.core.PlatformContact
import io.slychat.messenger.services.PlatformContacts
import nl.komponents.kovenant.Promise
import rx.Observable
import rx.subjects.PublishSubject
import java.util.*

class DesktopPlatformContacts : PlatformContacts {
    private val contactsUpdateSubject = PublishSubject.create<Unit>()
    override val contactsUpdated: Observable<Unit>
        get() = contactsUpdateSubject

    override fun fetchContacts(): Promise<List<PlatformContact>, Exception> = Promise.ofSuccess(ArrayList())
}