package com.vfpowertech.keytap.core.persistence.sqlite

import com.almworks.sqlite4java.SQLite
import com.vfpowertech.keytap.core.loadSharedLibFromResource
import com.vfpowertech.keytap.core.persistence.ContactInfo
import com.vfpowertech.keytap.core.persistence.DuplicateContactException
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class SQLiteContactsPersistenceManagerTest {
    companion object {
        @JvmStatic
        @BeforeClass
        fun loadLibrary() {
            SQLitePreKeyPersistenceManager::class.java.loadSharedLibFromResource("sqlite4java-linux-amd64-1.0.392")
            SQLite.loadLibrary()
        }
    }

    val contactA = ContactInfo("a@a.com", "a", "000-0000", "pubkey")
    val contactA2 = ContactInfo("a2@a.com", "a2", "001-0000", "pubkey")
    val contactC = ContactInfo("c@c.com", "c", "222-2222", "pubkey")
    val contactList = arrayListOf(
        contactA,
        contactA2,
        contactC
    )

    lateinit var persistenceManager: SQLitePersistenceManager
    lateinit var contactsPersistenceManager: SQLiteContactsPersistenceManager

    fun loadContactList() {
        for (contact in contactList)
            contactsPersistenceManager.add(contact).get()
    }

    @Before
    fun before() {
        persistenceManager = SQLitePersistenceManager(null, ByteArray(0), null)
        persistenceManager.init()
        contactsPersistenceManager = SQLiteContactsPersistenceManager(persistenceManager)
    }

    @After
    fun after() {
        persistenceManager.shutdown()
    }

    @Test
    fun `add should successfully store a contact`() {
        val contact = contactA
        contactsPersistenceManager.add(contact).get()
        val got = contactsPersistenceManager.get(contact.email).get()

        assertNotNull(got)
        assertEquals(contact, got)
    }

    @Test
    fun `getAll should return all stored contacts`() {
        val contacts = arrayListOf(
            ContactInfo("a@a.com", "a", "000-0000", "pubkey"),
            ContactInfo("b@b.com", "b", "000-0000", "pubkey")
        )

        for (contact in contacts)
            contactsPersistenceManager.add(contact)

        val got = contactsPersistenceManager.getAll().get()

        assertEquals(contacts, got)
    }

    @Test
    fun `put should throw DuplicateContactException when inserting a duplicate contact`() {
        contactsPersistenceManager.add(contactA).get()
        assertFailsWith(DuplicateContactException::class) {
            contactsPersistenceManager.add(contactA).get()
        }
    }

    @Test
    fun `update should update an existing contact`() {
        val original = contactA
        contactsPersistenceManager.add(original).get()
        val updated = original.copy(name = "b", phoneNumber = "111-1111", publicKey = "pubkey2")
        contactsPersistenceManager.update(updated).get()

        val got = contactsPersistenceManager.get(updated.email).get()
        assertEquals(updated, got)
    }

    @Test
    fun `update should throw InvalidContactException when an attempt to update a non-existent contact is made`() {
        assertFailsWith(InvalidContactException::class) {
            contactsPersistenceManager.update(contactA).get()
        }
    }

    @Test
    fun `searchByName should return nothing when no matching contacts exist`() {
        assertEquals(0, contactsPersistenceManager.searchByName("name").get().size)
    }

    @Test
    fun `searchByName should return all matching contacts`() {
        loadContactList()

        val expected = arrayListOf(contactA, contactA2)

        val got = contactsPersistenceManager.searchByName("a").get()

        assertEquals(expected, got)
    }

    @Test
    fun `searchByEmail should return nothing when no matching contacts exist`() {
        assertEquals(0, contactsPersistenceManager.searchByEmail("a@a.com").get().size)
    }

    @Test
    fun `searchByEmail should return all matching contacts`() {
        loadContactList()

        val expected = arrayListOf(contactA, contactA2)

        val got = contactsPersistenceManager.searchByEmail("a.com").get()

        assertEquals(expected, got)
    }

    @Test
    fun `searchByPhoneNumber should return nothing when no matching contacts exist`() {
        assertEquals(0, contactsPersistenceManager.searchByPhoneNumber("000-0000").get().size)
    }

    @Test
    fun `searchByPhoneNumber should return all matching contacts`() {
        loadContactList()

        val expected = arrayListOf(contactA, contactA2)

        val got = contactsPersistenceManager.searchByPhoneNumber("0000").get()

        assertEquals(expected, got)
    }
}
