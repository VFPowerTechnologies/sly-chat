package io.slychat.messenger.core.persistence.sqlite

import io.slychat.messenger.core.PlatformContact
import io.slychat.messenger.core.SlyAddress
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.*
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import java.util.*
import kotlin.test.*

class SQLiteContactsPersistenceManagerTest {
    companion object {
        @JvmStatic
        @BeforeClass
        fun loadLibrary() {
            SQLitePreKeyPersistenceManager::class.java.loadSQLiteLibraryFromResources()
        }
    }

    val contactId = UserId(1)
    val testMessage = "test message"

    val contactA = ContactInfo(contactId, "a@a.com", "a", false, "000-0000", "pubkey")
    val contactA2 = ContactInfo(UserId(2), "a2@a.com", "a2", false, "001-0000", "pubkey")
    val contactC = ContactInfo(UserId(3), "c@c.com", "c", false, "222-2222", "pubkey")
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

    fun setConversationInfo(userId: UserId, unreadCount: Int, lastMessage: String?) {
        persistenceManager.runQuery { connection ->
            connection.prepare("UPDATE conversation_info SET unread_count=?, last_message=? WHERE contact_id=?").use { stmt ->
                stmt.bind(1, unreadCount)
                stmt.bind(2, lastMessage)
                stmt.bind(3, userId.long)
                stmt.step()
            }
        }
    }

    @Before
    fun before() {
        persistenceManager = SQLitePersistenceManager(null, null, null)
        persistenceManager.init()
        contactsPersistenceManager = SQLiteContactsPersistenceManager(persistenceManager)
    }

    @After
    fun after() {
        persistenceManager.shutdown()
    }

    fun doesConvTableExist(userId: UserId): Boolean =
        persistenceManager.runQuery { ConversationTable.exists(it, userId) }.get()

    @Test
    fun `add should successfully add a contact and create a conversation table`() {
        val contact = contactA
        contactsPersistenceManager.add(contact).get()
        val got = contactsPersistenceManager.get(contact.id).get()

        assertNotNull(got)
        assertEquals(contact, got)
        assertTrue(doesConvTableExist(contact.id), "Conversation table is missing")
    }

    @Test
    fun `add should do nothing if the contact already exists`() {
        val contact = contactA
        for (i in 0..2)
            contactsPersistenceManager.add(contact).get()
    }

    @Test
    fun `getAll should return all stored contacts`() {
        val contacts = arrayListOf(
            ContactInfo(UserId(0), "a@a.com", "a", false, "000-0000", "pubkey"),
            ContactInfo(UserId(1), "b@b.com", "b", false, "000-0000", "pubkey")
        )

        for (contact in contacts)
            contactsPersistenceManager.add(contact)

        val got = contactsPersistenceManager.getAll().get()

        assertEquals(contacts, got)
    }

    @Test
    fun `update should update an existing contact`() {
        val original = contactA
        contactsPersistenceManager.add(original).get()
        val updated = original.copy(name = "b", phoneNumber = "111-1111", publicKey = "pubkey2")
        contactsPersistenceManager.update(updated).get()

        val got = contactsPersistenceManager.get(updated.id).get()
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

    @Test
    fun `remove should delete the contact and its conversation table`() {
        contactsPersistenceManager.add(contactA)

        contactsPersistenceManager.remove(contactA)

        val got = contactsPersistenceManager.get(contactA.id).get()

        assertNull(got)
        assertFalse(doesConvTableExist(contactA.id))
    }

    @Test
    fun `getAllConversations should return an empty list if no conversations are available`() {
        assertTrue(contactsPersistenceManager.getAllConversations().get().isEmpty())
    }

    @Test
    fun `getAllConversations should return all available conversations`() {
        loadContactList()

        val got = contactsPersistenceManager.getAllConversations().get()

        assertEquals(3, got.size)
    }

    @Test
    fun `getAllConversations should return a last message field if messages are available`() {
        loadContactList()

        setConversationInfo(contactId, 2, testMessage)

        val convos = contactsPersistenceManager.getAllConversations().get()

        val a = convos.find { it.contact.id == contactId }!!
        val a2 = convos.find { it.contact == contactA2 }!!

        assertEquals(testMessage, a.info.lastMessage)
        assertNull(a2.info.lastMessage)
    }

    @Test
    fun `getConversation should return a conversation if it exists`() {
        loadContactList()

        contactsPersistenceManager.getConversationInfo(contactA.id).get()
    }

    @Test
    fun `getConversation should include unread message counts in a conversation`() {
        loadContactList()

        val before = contactsPersistenceManager.getConversationInfo(contactA.id).get()
        assertEquals(0, before.unreadMessageCount)

        setConversationInfo(contactId, 1, testMessage)

        val after = contactsPersistenceManager.getConversationInfo(contactA.id).get()
        assertEquals(1, after.unreadMessageCount)
    }

    @Test
    fun `getConversation should throw InvalidConversationException if the given conversation doesn't exist`() {
        assertFailsWith(InvalidConversationException::class) {
            contactsPersistenceManager.getConversationInfo(contactA.id).get()
        }
    }

    @Test
    fun `markConversationAsRead should mark all unread messages as read`() {
        loadContactList()

        setConversationInfo(contactId, 2, testMessage)

        contactsPersistenceManager.markConversationAsRead(contactA.id).get()

        val got = contactsPersistenceManager.getConversationInfo(contactA.id).get()
        assertEquals(0, got.unreadMessageCount)
    }

    @Test
    fun `markConversationAsRead should throw InvalidConversationException if the given conversation doesn't exist`() {
        assertFailsWith(InvalidConversationException::class) {
            contactsPersistenceManager.markConversationAsRead(contactA.id).get()
        }
    }

    @Test
    fun `findMissing should ignore contacts with found emails`() {
        loadContactList()

        val pcontactA = PlatformContact(contactA.name, listOf(contactA.email), listOf())
        val pcontactD = PlatformContact("D", listOf("d@a.com"), listOf())

        val contacts = arrayListOf(pcontactA, pcontactD)
        val got = contactsPersistenceManager.findMissing(contacts).get()
        assertEquals(listOf(pcontactD), got)
    }

    @Test
    fun `findMissing should ignore contacts with found phone numbers`() {
        loadContactList()

        val pcontactA = PlatformContact(contactA.name, listOf(), listOf(contactA.phoneNumber!!))
        val pcontactD = PlatformContact("D", listOf("d@a.com"), listOf())

        val contacts = arrayListOf(pcontactA, pcontactD)
        val got = contactsPersistenceManager.findMissing(contacts).get()
        assertEquals(listOf(pcontactD), got)
    }

    @Test
    fun `findMissing should ignore contacts with found emails or phone numbers`() {
        loadContactList()

        val pcontactA = PlatformContact(contactA.name, listOf(contactA.email), listOf(contactA.phoneNumber!!))
        val pcontactD = PlatformContact("D", listOf("d@a.com"), listOf())

        val contacts = arrayListOf(pcontactA, pcontactD)
        val got = contactsPersistenceManager.findMissing(contacts).get()
        assertEquals(listOf(pcontactD), got)

    }

    @Test
    fun `findMissing should ignore contacts with no email or phone number`() {
        loadContactList()

        val pcontactA = PlatformContact(contactA.name, listOf(), listOf())
        val pcontactD = PlatformContact("D", listOf("d@a.com"), listOf())

        val contacts = arrayListOf(pcontactA, pcontactD)
        val got = contactsPersistenceManager.findMissing(contacts).get()
        assertEquals(listOf(pcontactD), got)
    }

    @Test
    fun `getDiff should return a proper diff`() {
        val userA = ContactInfo(UserId(0), "a@a.com", "a", false, "0", "pk")
        val userB = ContactInfo(UserId(1), "b@a.com", "a", false, "0", "pk")
        val userC = ContactInfo(UserId(2), "c@a.com", "a", false, "0", "pk")

        for (user in listOf(userA, userB))
            contactsPersistenceManager.add(user).get()

        val remoteContacts = listOf(userA.id, userC.id)

        val diff = contactsPersistenceManager.getDiff(remoteContacts).get()

        val expected = ContactListDiff(setOf(userC.id), setOf(userB.id))

        assertEquals(expected, diff)
    }

    @Test
    fun `exists(UserId) should return true if a user exists`() {
        contactsPersistenceManager.add(contactA).get()

        assertTrue(contactsPersistenceManager.exists(contactA.id).get(), "User should exist")
    }

    @Test
    fun `exists(UserId) should return false if a user exists`() {
        assertFalse(contactsPersistenceManager.exists(contactA.id).get(), "User shouldn't exist")
    }

    @Test
    fun `exists(List) should return all existing users in the asked set`() {
        val contacts = listOf(contactA, contactA2)
        for (contact in contacts)
            contactsPersistenceManager.add(contact).get()

        val query = hashSetOf(contactC.id)
        query.addAll(contacts.map { it.id })

        val exists = contactsPersistenceManager.exists(query).get()

        val shouldExist = HashSet<UserId>()
        shouldExist.addAll(contacts.map { it.id })

        assertEquals(shouldExist, exists, "Invalid users")
    }

    @Test
    fun `getPending should return only pending users`() {
        val contactA = ContactInfo(UserId(1), "a@a.com", "a", true, null, "pk")
        val contactB = ContactInfo(UserId(2), "b@a.com", "b", false, null, "pk")
        val contacts = listOf(contactA, contactB)

        contacts.forEach { contactsPersistenceManager.add(it).get() }

        val pending = contactsPersistenceManager.getPending().get()

        assertEquals(listOf(contactA), pending, "Invalid pending contacts")
    }

    @Test
    fun `markAccepted should mark the given user as no longer pending`() {
        val contactA = ContactInfo(UserId(1), "a@a.com", "a", true, null, "pk")
        contactsPersistenceManager.add(contactA).get()

        contactsPersistenceManager.markAccepted(setOf(contactA.id)).get()

        val updated = assertNotNull(contactsPersistenceManager.get(contactA.id).get(), "Missing user")

        assertFalse(updated.isPending, "Pending state not updated")
    }

    @Test
    fun `getUnadded should return only user ids for which packages have no corresponding contacts entry`() {
        contactsPersistenceManager.add(contactA).get()

        val contactAddress = SlyAddress(contactId, 1)
        val newId = UserId(contactId.long+1)
        val newAddress1 = SlyAddress(newId, 1)
        val newAddress2 = SlyAddress(newId, 2)

        //XXX this is kinda nasty, but this function needs access to tables from both
        val messagePersistenceManager = SQLiteMessagePersistenceManager(persistenceManager)

        val packages = listOf(
            Package(PackageId(contactAddress, "msgid"), 0, ""),
            Package(PackageId(newAddress1, "msgid"), 0, ""),
            Package(PackageId(newAddress2, "msgid2"), 0, "")
        )

        messagePersistenceManager.addToQueue(packages).get()

        val unadded = contactsPersistenceManager.getUnadded().get()

        assertEquals(setOf(newId), unadded, "Invalid user id list")
    }
}
