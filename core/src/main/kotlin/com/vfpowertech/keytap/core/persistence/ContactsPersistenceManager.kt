package com.vfpowertech.keytap.core.persistence

import nl.komponents.kovenant.Promise

/** Manages contacts. */
interface ContactsPersistenceManager {
    fun get(email: String): Promise<ContactInfo?, Exception>
    fun getAll(): Promise<List<ContactInfo>, Exception>
    fun getAllConversations(): Promise<List<Conversation>, Exception>

    /** Adds a new contact and conversation for a contact. */
    fun add(contactInfo: ContactInfo): Promise<Unit, Exception>
    /** Updates the given contact's info. */
    fun update(contactInfo: ContactInfo): Promise<Unit, Exception>
    /** Removes a contact and their associated conversation. */
    fun remove(contactInfo: ContactInfo): Promise<Unit, Exception>

    fun searchByPhoneNumber(phoneNumber: String): Promise<List<ContactInfo>, Exception>
    fun searchByName(name: String): Promise<List<ContactInfo>, Exception>
    fun searchByEmail(email: String): Promise<List<ContactInfo>, Exception>
}