package io.slychat.messenger.services.ui

import com.vfpowertech.jsbridge.processor.annotations.JSToJavaGenerate
import io.slychat.messenger.core.UserId
import nl.komponents.kovenant.Promise

/** Contact management. */
@JSToJavaGenerate("ContactsService")
interface UIContactsService {
    /**
     * Listener for contact-related events. On registration, will send any events related to statuses (eg: contact
     * sync status) to the listener.
     *
     * Examples events:
     *
     * When another user wants to add you as a contact, and the privacy settings require user confirmation. It's the
     * responsibility of the UI to present the information to the user and then add the contact afterwards if the user wishes it.
     *
     * When a contact sync begins or ends.
     */
    fun addContactEventListener(listener: (UIContactEvent) -> Unit)

    /** Retrieve list of contacts. UIContact.id will not be null. */
    fun getContacts(): Promise<List<UIContactInfo>, Exception>

    /**
     * Add a new contact with the given info.
     */
    fun addNewContact(uiContactInfo: UIContactInfo): Promise<UIContactInfo, Exception>

    /**
     * Updates the given contact with the given info. newContactInfo.id must not be null.
     */
    fun updateContact(newContactInfo: UIContactInfo): Promise<UIContactInfo, Exception>

    /**
     * Remove the given contact with the given info.
     */
    fun removeContact(uiContactInfo: UIContactInfo): Promise<Unit, Exception>

    /**
     * Fetch the contact details if contact exist. Email or phoneNumber must not be null.
     */
    fun fetchNewContactInfo(email: String?, phoneNumber: String?): Promise<UINewContactResult, Exception>

    fun getBlockList(): Promise<Set<UserId>, Exception>

    fun block(userId: UserId): Promise<Unit, Exception>

    fun unblock(userId: UserId): Promise<Unit, Exception>
}
