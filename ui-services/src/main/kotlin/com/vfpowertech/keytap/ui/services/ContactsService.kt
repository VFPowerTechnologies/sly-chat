package com.vfpowertech.keytap.ui.services

import com.vfpowertech.jsbridge.processor.annotations.JSToJavaGenerate
import nl.komponents.kovenant.Promise

/** Contact management. */
@JSToJavaGenerate
interface ContactsService {
    /** Retrieve list of contacts. UIContact.id will not be null. */
    fun getContacts(): Promise<List<UIContactDetails>, Exception>

    /**
     * Add a new contact with the given info. UIContact.id must be null.
     *
     * @throws IllegalArgumentException newContactInfo.id wasn't null.
     */
    fun addNewContact(contactDetails: UIContactDetails): Promise<UIContactDetails, Exception>

    /**
     * Updates the given contact with the given info. newContactInfo.id must not be null.
     *
     * @throws InvalidContactException Given Contact doesn't exist.
     * @throws IllegalArgumentException newContactInfo.id was null.
     */
    fun updateContact(newContactDetails: UIContactDetails): Promise<UIContactDetails, Exception>

    /**
     * Remove the given contact with the given info. contactDetails.id must not be null.
     *
     * @throws InvalidContactException Given Contact doesn't exist.
     * @throws IllegalArgumentException contactDetails.id was null.
     */
    fun removeContact(contactDetails: UIContactDetails): Promise<Unit, Exception>
}
