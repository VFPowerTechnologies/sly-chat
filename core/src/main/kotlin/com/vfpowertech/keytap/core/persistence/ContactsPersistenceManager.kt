package com.vfpowertech.keytap.core.persistence

import nl.komponents.kovenant.Promise

interface ContactsPersistenceManager {
    fun get(email: String): Promise<ContactInfo?, Exception>
    fun getAll(): Promise<List<ContactInfo>, Exception>
    fun add(contactInfo: ContactInfo): Promise<Unit, Exception>
    fun update(contactInfo: ContactInfo): Promise<Unit, Exception>

    fun searchByPhoneNumber(phoneNumber: String): Promise<List<ContactInfo>, Exception>
    fun searchByName(name: String): Promise<List<ContactInfo>, Exception>
    fun searchByEmail(email: String): Promise<List<ContactInfo>, Exception>
}