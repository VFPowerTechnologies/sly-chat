package com.vfpowertech.keytap.ui.services

import com.vfpowertech.jsbridge.processor.annotations.JSToJavaGenerate
import nl.komponents.kovenant.Promise

/** Contact management. */
@JSToJavaGenerate
interface ContactsService {
    /** Retrieve list of contacts. UIContact.id will not be null. */
    fun getContacts(): Promise<List<UIContactInfo>, Exception>

    /**
     * Add a new contact with the given info. UIContact.id must be null.
     *
     * @throws IllegalArgumentException newContactInfo.id wasn't null.
     */
    fun addNewContact(contactInfo: UIContactInfo): Promise<UIContactInfo, Exception>

    /**
     * Updates the given contact with the given info. newContactInfo.id must not be null.
     *
     * @throws InvalidContactException Given Contact doesn't exist.
     * @throws IllegalArgumentException newContactInfo.id was null.
     */
    fun updateContact(newContactInfo: UIContactInfo)
}
