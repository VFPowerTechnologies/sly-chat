package io.slychat.messenger.core.persistence.sqlite

import io.slychat.messenger.core.PlatformContact
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.currentTimestamp
import io.slychat.messenger.core.persistence.*
import io.slychat.messenger.core.randomUserId
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

    val contactA = ContactInfo(contactId, "a@a.com", "a", AllowedMessageLevel.ALL, false, "000-0000", "pubkey")
    val contactA2 = ContactInfo(UserId(2), "a2@a.com", "a2", AllowedMessageLevel.ALL, false, "001-0000", "pubkey")
    val contactC = ContactInfo(UserId(3), "c@c.com", "c", AllowedMessageLevel.ALL, false, "222-2222", "pubkey")
    val contactList = arrayListOf(
        contactA,
        contactA2,
        contactC
    )

    lateinit var persistenceManager: SQLitePersistenceManager
    lateinit var contactsPersistenceManager: SQLiteContactsPersistenceManager

    var dummyContactCounter = 0L
    fun createDummyContact(
        allowedMessageLevel: AllowedMessageLevel = AllowedMessageLevel.ALL
    ): ContactInfo {
        val v = dummyContactCounter
        dummyContactCounter += 1
        val id = UserId(v)

        return ContactInfo(id, "$v@a.com", "$v", allowedMessageLevel, false, "$v", "$v")
    }

    fun insertDummyContact(
        allowedMessageLevel: AllowedMessageLevel = AllowedMessageLevel.ALL
    ): ContactInfo {
        val info = createDummyContact(allowedMessageLevel)
        contactsPersistenceManager.add(info).get()
        return info
    }

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

    fun testContactAdd(contact: ContactInfo, shouldConvTableBeCreated: Boolean) {
        assertTrue(contactsPersistenceManager.add(contact).get())
        val got = assertNotNull(contactsPersistenceManager.get(contact.id).get(), "User not added")

        assertEquals(contact, got, "Stored info doesn't match initial info")

        val doesConvTableExist = doesConvTableExist(contact.id)
        if (shouldConvTableBeCreated)
            assertTrue(doesConvTableExist, "Conversation table is missing")
        else
            assertFalse(doesConvTableExist, "Conversation table was created")

        val update = RemoteContactUpdate(contact.id, RemoteContactUpdateType.ADD)
        assertEquals(listOf(update), contactsPersistenceManager.getRemoteUpdates().get(), "Invalid remote update list")
    }

    @Test
    fun `add should successfully add a contact, create a conversation table and add a corresponding remote update for an ALL user`() {
        testContactAdd(createDummyContact(AllowedMessageLevel.ALL), true)
    }

    @Test
    fun `add should successfully add a contact, and add a corresponding remote update for a GROUP_ONLY user`() {
        testContactAdd(createDummyContact(AllowedMessageLevel.GROUP_ONLY), false)
    }

    @Test
    fun `add should do nothing and return false if the contact already exists and the message level is the same`() {
        val contact = contactA
        contactsPersistenceManager.add(contact).get()

        assertFalse(contactsPersistenceManager.add(contact).get(), "Contact not considered duplicate")
        assertEquals(1, contactsPersistenceManager.getRemoteUpdates().get().size, "Invalid number of remote updates")
    }

    @Test
    fun `add should update the message level for an existing user`() {
        val contact = insertDummyContact(AllowedMessageLevel.GROUP_ONLY)
        val newContact = contact.copy(allowedMessageLevel = AllowedMessageLevel.ALL)

        assertTrue(contactsPersistenceManager.add(newContact).get(), "No update")

        val info = assertNotNull(contactsPersistenceManager.get(newContact.id).get(), "Missing user")

        assertEquals(newContact, info, "Invalid contact info")
    }

    @Test
    fun `add should create a conversation table for an existing user if the new message level is ALL`() {
        val contact = insertDummyContact(AllowedMessageLevel.GROUP_ONLY)
        val newContact = contact.copy(allowedMessageLevel = AllowedMessageLevel.ALL)

        testContactAdd(newContact, true)
    }

    fun testAddContactMulti(existingContacts: Collection<ContactInfo>, newContacts: Collection<ContactInfo>) {
        contactsPersistenceManager.add(existingContacts).get()

        val allContacts = ArrayList<ContactInfo>()
        allContacts.addAll(existingContacts)
        allContacts.addAll(newContacts)

        val added = contactsPersistenceManager.add(allContacts).get()

        assertEquals(newContacts.toSet(), added, "Invalid added contacts")

        val expectedUpdates = allContacts.map { RemoteContactUpdate(it.id, RemoteContactUpdateType.ADD) }.sortedBy { it.userId.long }

        val updates = contactsPersistenceManager.getRemoteUpdates().get().sortedBy { it.userId.long }

        assertEquals(expectedUpdates, updates, "Invalid remote update list")

    }

    @Test
    fun `add(List) should return the list of new contacts added and add corresponding remote updates (pending=false)`() {
        testAddContactMulti(listOf(contactA), listOf(contactA2, contactC))
    }

    @Test
    fun `getAll should return all stored contacts`() {
        val contacts = arrayListOf(
            ContactInfo(UserId(0), "a@a.com", "a", AllowedMessageLevel.ALL, false, "000-0000", "pubkey"),
            ContactInfo(UserId(1), "b@b.com", "b", AllowedMessageLevel.ALL, false, "000-0000", "pubkey")
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
    fun `remove should set the contact message level to GROUP_ONLY for an existing user`() {
        contactsPersistenceManager.add(contactA).get()

        val wasRemoved = contactsPersistenceManager.remove(contactA.id).get()

        assertTrue(wasRemoved, "wasRemoved is false")

        val got = assertNotNull(contactsPersistenceManager.get(contactA.id).get(), "Missing user info")

        assertEquals(AllowedMessageLevel.GROUP_ONLY, got.allowedMessageLevel, "Invalid message level")
    }

    @Test
    fun `remove should remove the convo log for an existing user`() {
        contactsPersistenceManager.add(contactA).get()

        contactsPersistenceManager.remove(contactA.id).get()

        assertFalse(doesConvTableExist(contactA.id))
    }

    @Test
    fun `remove should reset the conversation info for an existing user`() {
        val contact = insertDummyContact(AllowedMessageLevel.ALL)

        contactsPersistenceManager.setConversationInfo(contact.id, ConversationInfo(contact.id, 1, "last message", currentTimestamp()))

        contactsPersistenceManager.remove(contact.id).get()

        val convoInfo = assertNotNull(contactsPersistenceManager.getConversationInfo(contact.id).get(), "Missing conversation info")
        val initialConvoInfo = ConversationInfo(contact.id, 0, null, null)

        assertEquals(initialConvoInfo, convoInfo, "Conversation info not reset")
    }

    @Test
    fun `remove should return false if no such contact existed`() {
        val wasRemoved = contactsPersistenceManager.remove(randomUserId()).get()

        assertFalse(wasRemoved, "wasRemoved is true")
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
        val userA = ContactInfo(UserId(0), "a@a.com", "a", AllowedMessageLevel.ALL, false, "0", "pk")
        val userB = ContactInfo(UserId(1), "b@a.com", "a", AllowedMessageLevel.ALL, false, "0", "pk")
        val userC = ContactInfo(UserId(2), "c@a.com", "a", AllowedMessageLevel.ALL, false, "0", "pk")

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
    fun `addRemoteUpdates should register added updates`() {
        val remoteUpdates = listOf(
            RemoteContactUpdate(UserId(1), RemoteContactUpdateType.ADD),
            RemoteContactUpdate(UserId(2), RemoteContactUpdateType.REMOVE)
        )
        contactsPersistenceManager.addRemoteUpdate(remoteUpdates).get()

        val got = contactsPersistenceManager.getRemoteUpdates().get()

        assertEquals(remoteUpdates, got, "Invalid remote updates")
    }

    @Test
    fun `addRemoteUpdates should overwrite an existing records`() {
        val userId = UserId(1)
        val update1 = RemoteContactUpdate(userId, RemoteContactUpdateType.ADD)
        val update2 = RemoteContactUpdate(userId, RemoteContactUpdateType.REMOVE)

        contactsPersistenceManager.addRemoteUpdate(listOf(update1)).get()
        contactsPersistenceManager.addRemoteUpdate(listOf(update2)).get()

        val got = contactsPersistenceManager.getRemoteUpdates().get()

        assertEquals(listOf(update2), got, "Invalid remote updates")
    }

    @Test
    fun `remoteRemoteUpdates should remove only the given updates`() {
        val update1 = RemoteContactUpdate(UserId(1), RemoteContactUpdateType.ADD)
        val update2 = RemoteContactUpdate(UserId(2), RemoteContactUpdateType.ADD)

        contactsPersistenceManager.addRemoteUpdate(listOf(update1, update2)).get()

        contactsPersistenceManager.removeRemoteUpdates(listOf(update2)).get()

        val got = contactsPersistenceManager.getRemoteUpdates().get()

        assertEquals(listOf(update1), got, "Invalid remote updates")
    }

    @Test
    fun `getBlockList should return only entries with BLOCKED message level`() {
        val all = createDummyContact(AllowedMessageLevel.ALL)
        val groupOnly = createDummyContact(AllowedMessageLevel.GROUP_ONLY)
        val blocked = createDummyContact(AllowedMessageLevel.BLOCKED)
        val contacts = listOf(all, groupOnly, blocked)

        contacts.forEach { contactsPersistenceManager.add(it) }

        val blockList = contactsPersistenceManager.getBlockList().get()

        assertEquals(setOf(blocked.id), blockList, "Invalid block list")
    }

    @Test
    fun `filterBlocked should return only users without BLOCKED message level`() {
        val all = createDummyContact(AllowedMessageLevel.ALL)
        val groupOnly = createDummyContact(AllowedMessageLevel.GROUP_ONLY)
        val blocked = createDummyContact(AllowedMessageLevel.BLOCKED)
        val contacts = listOf(all, groupOnly, blocked)

        contacts.forEach { contactsPersistenceManager.add(it) }

        val notBlocked = contactsPersistenceManager.filterBlocked(contacts.map { it.id }).get()

        assertEquals(setOf(all.id, groupOnly.id), notBlocked, "Invalid filtered list")
    }

    @Test
    fun `filterBlocked should not filter out unadded users`() {
        val contactIds = setOf(UserId(1))

        val notBlocked = contactsPersistenceManager.filterBlocked(contactIds).get()

        assertEquals(contactIds, notBlocked, "Invalid filtered list")
    }

    fun testBlock(contact: ContactInfo) {
        contactsPersistenceManager.add(contact).get()

        contactsPersistenceManager.block(contact.id).get()

        val info = assertNotNull(contactsPersistenceManager.get(contact.id).get(), "Missing contact")

        assertEquals(AllowedMessageLevel.BLOCKED, info.allowedMessageLevel, "Invalid message level")
    }

    @Test
    fun `block should update the given user allowedMessageLevel to BLOCKED for an ALL user`() {
        testBlock(createDummyContact(AllowedMessageLevel.ALL))
    }

    @Test
    fun `block should remove a conversation table for an ALL user`() {
        val contact = createDummyContact(AllowedMessageLevel.ALL)

        contactsPersistenceManager.add(contact).get()

        contactsPersistenceManager.block(contact.id).get()

        assertFalse(doesConvTableExist(contact.id), "Conversation table not removed")
    }

    @Test
    fun `block should update the given user allowedMessageLevel to BLOCKED for a GROUP_ONLY user`() {
        testBlock(createDummyContact(AllowedMessageLevel.GROUP_ONLY))
    }

    @Test
    fun `block should do nothing for an already blocked user`() {
        testBlock(createDummyContact(AllowedMessageLevel.GROUP_ONLY))
    }


    @Test
    fun `block should do nothing for a non-existent user`() {
        contactsPersistenceManager.block(randomUserId()).get()
    }

    @Test
    fun `unblock should update the given user allowedMessageLevel to GROUP_ONLY for an existing blocked user`() {
        val userId = insertDummyContact(AllowedMessageLevel.BLOCKED).id

        contactsPersistenceManager.unblock(userId).get()

        val info = assertNotNull(contactsPersistenceManager.get(userId).get(), "Missing user")

        assertEquals(AllowedMessageLevel.GROUP_ONLY, info.allowedMessageLevel, "Invalid message level")
    }

    @Test
    fun `unblock should do nothing for a non-existent user`() {
        contactsPersistenceManager.unblock(randomUserId()).get()
    }
}
