package com.vfpowertech.keytap.ui.services.impl

import com.vfpowertech.keytap.ui.services.ContactsService
import com.vfpowertech.keytap.ui.services.UIContactInfo
import nl.komponents.kovenant.Promise

class ContactsServiceImpl : ContactsService {
    override fun getContacts(): Promise<List<UIContactInfo>, Exception> {
        return Promise.ofSuccess(arrayListOf(
            UIContactInfo(0, "Contact A", "000-000-0000", "a@a.com"),
            UIContactInfo(0, "Contact B", "111-111-1111", "b@b.com")
        ))
    }

    override fun addNewContact(contactInfo: UIContactInfo): Promise<Unit, Exception> {
        return Promise.ofSuccess(Unit)
    }
}