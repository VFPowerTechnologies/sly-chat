package com.vfpowertech.keytap.services.ui

import com.vfpowertech.jsbridge.processor.annotations.JSToJavaGenerate
import nl.komponents.kovenant.Promise

/** Contact management. */
@JSToJavaGenerate("ContactsService")
interface UIContactsService {
    /** Retrieve list of contacts. UIContact.id will not be null. */
    fun getContacts(): Promise<List<UIContactDetails>, Exception>

    /**
     * Add a new contact with the given info.
     */
    fun addNewContact(contactDetails: UIContactDetails): Promise<UIContactDetails, Exception>

    /**
     * Updates the given contact with the given info. newContactInfo.id must not be null.
     */
    fun updateContact(newContactDetails: UIContactDetails): Promise<UIContactDetails, Exception>

    /**
     * Remove the given contact with the given info.
     */
    fun removeContact(contactDetails: UIContactDetails): Promise<Unit, Exception>

    /**
     * Fetch the contact details if contact exist. Email or phoneNumber must not be null.
     */
    fun fetchNewContactInfo(email: String?, phoneNumber: String?): Promise<UINewContactResult, Exception>
}
