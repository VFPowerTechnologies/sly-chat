package com.vfpowertech.keytap.services.ui.dummy

import com.vfpowertech.keytap.core.UserId
import com.vfpowertech.keytap.services.ui.UIContactDetails
import com.vfpowertech.keytap.services.ui.UIContactsService
import com.vfpowertech.keytap.services.ui.UINewContactResult
import nl.komponents.kovenant.Promise

class DummyUIContactsService : UIContactsService {
    override fun addContactListSyncListener(listener: (Boolean) -> Unit) {
    }

    private val contacts = hashMapOf(
        "Contact A" to UIContactDetails(UserId(1000), "Contact A", "000-000-0000", "a@a.com", "dummyPublicKey"),
        "Contact B" to UIContactDetails(UserId(1001), "Contact B", "111-111-1111", "b@b.com", "dummyPublicKedy")
    )

    override fun updateContact(newContactDetails: UIContactDetails): Promise<UIContactDetails, Exception> {
        return Promise.ofSuccess<UIContactDetails, Exception>(newContactDetails)
    }

    override fun getContacts(): Promise<List<UIContactDetails>, Exception> {
        return Promise.ofSuccess(contacts.values.toList())
    }

    override fun addNewContact(contactDetails: UIContactDetails): Promise<UIContactDetails, Exception> {
        return Promise.ofSuccess(UIContactDetails(UserId(1002), "", "", "", ""))
    }

    override fun fetchNewContactInfo(email: String?, phoneNumber: String?): Promise<UINewContactResult, Exception>{
        return Promise.ofSuccess(UINewContactResult(true, null, null))
    }

    override fun removeContact(contactDetails: UIContactDetails): Promise<Unit, Exception> {
        return Promise.ofSuccess(Unit)
    }
}

