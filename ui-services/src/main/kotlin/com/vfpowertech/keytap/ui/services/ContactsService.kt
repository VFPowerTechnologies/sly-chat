package com.vfpowertech.keytap.ui.services

import com.vfpowertech.jsbridge.processor.annotations.JSToJavaGenerate
import nl.komponents.kovenant.Promise

/** Contact management. */
@JSToJavaGenerate
interface ContactsService {
    fun getContacts(): Promise<List<UIContactInfo>, Exception>

    fun addNewContact(contactInfo: UIContactInfo): Promise<Unit, Exception>
}
