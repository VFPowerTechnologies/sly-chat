package com.vfpowertech.keytap.desktop

import com.vfpowertech.keytap.core.PlatformContact
import com.vfpowertech.keytap.services.PlatformContacts
import nl.komponents.kovenant.Promise
import rx.Observable
import rx.subjects.PublishSubject
import java.util.*

class DesktopPlatformContacts : PlatformContacts {
    private val contactsUpdateSubject = PublishSubject.create<Unit>()
    override val contactsUpdated: Observable<Unit> = contactsUpdateSubject

    override fun fetchContacts(): Promise<List<PlatformContact>, Exception> = Promise.ofSuccess(ArrayList())
}