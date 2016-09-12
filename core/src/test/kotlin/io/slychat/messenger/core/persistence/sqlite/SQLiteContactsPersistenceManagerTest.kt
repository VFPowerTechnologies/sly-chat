package io.slychat.messenger.core.persistence.sqlite

import io.slychat.messenger.core.*
import io.slychat.messenger.core.crypto.unhexify
import io.slychat.messenger.core.persistence.*
import org.assertj.core.api.Assertions.assertThat
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

    val contactA = ContactInfo(contactId, "a@a.com", "a", AllowedMessageLevel.ALL, "000-0000", "pubkey")
    val contactA2 = ContactInfo(UserId(2), "a2@a.com", "a2", AllowedMessageLevel.ALL, "001-0000", "pubkey")
    val contactC = ContactInfo(UserId(3), "c@c.com", "c", AllowedMessageLevel.ALL, "222-2222", "pubkey")
    val contactList = arrayListOf(
        contactA,
        contactA2,
        contactC
    )

    lateinit var persistenceManager: SQLitePersistenceManager
    lateinit var contactsPersistenceManager: SQLiteContactsPersistenceManager
    lateinit var conversationInfoTestUtils: ConversationInfoTestUtils

    var dummyContactCounter = 0L
    fun createDummyContact(
        allowedMessageLevel: AllowedMessageLevel = AllowedMessageLevel.ALL
    ): ContactInfo {
        val v = dummyContactCounter
        dummyContactCounter += 1
        val id = UserId(v)

        return ContactInfo(id, "$v@a.com", "$v", allowedMessageLevel, "$v", "$v")
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

    @Before
    fun before() {
        persistenceManager = SQLitePersistenceManager(null, null, null)
        persistenceManager.init()
        contactsPersistenceManager = SQLiteContactsPersistenceManager(persistenceManager)
        conversationInfoTestUtils = ConversationInfoTestUtils(persistenceManager)
    }

    @After
    fun after() {
        persistenceManager.shutdown()
    }

    fun fetchContactInfo(userId: UserId): ContactInfo {
        return assertNotNull(contactsPersistenceManager.get(userId).get(), "Missing user: $userId")
    }

    fun clearRemoteUpdates() {
        contactsPersistenceManager.removeRemoteUpdates(contactsPersistenceManager.getRemoteUpdates().get().map { it.userId }).get()
    }

    fun testContactAdd(contact: ContactInfo, shouldConvTableBeCreated: Boolean) {
        assertTrue(contactsPersistenceManager.add(contact).get())
        val got = assertNotNull(contactsPersistenceManager.get(contact.id).get(), "User not added")

        assertEquals(contact, got, "Stored info doesn't match initial info")

        val doesConvTableExist = conversationInfoTestUtils.doesConvTableExist(ConversationId(contact.id))
        if (shouldConvTableBeCreated)
            assertTrue(doesConvTableExist, "Conversation table is missing")
        else
            assertFalse(doesConvTableExist, "Conversation table was created")

        val update = AddressBookUpdate.Contact(contact.id, contact.allowedMessageLevel)
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
    fun `add should update the message level for an existing user if the level is higher than the previous one`() {
        val contact = insertDummyContact(AllowedMessageLevel.GROUP_ONLY)
        val newContact = contact.copy(allowedMessageLevel = AllowedMessageLevel.ALL)

        assertTrue(contactsPersistenceManager.add(newContact).get(), "No update")

        val info = assertNotNull(contactsPersistenceManager.get(newContact.id).get(), "Missing user")

        assertEquals(newContact, info, "Invalid contact info")
    }

    @Test
    fun `add should not modify the message level for an existing user if the level is lower than the previous one`() {
        val contact = insertDummyContact(AllowedMessageLevel.ALL)
        val newContact = contact.copy(allowedMessageLevel = AllowedMessageLevel.GROUP_ONLY)

        assertFalse(contactsPersistenceManager.add(newContact).get(), "Was updated")

        val info = assertNotNull(contactsPersistenceManager.get(newContact.id).get(), "Missing user")

        assertEquals(contact, info, "Invalid contact info")
    }

    @Test
    fun `add should create a remote update if the message level of an existing user is modified`() {
        val contact = insertDummyContact(AllowedMessageLevel.GROUP_ONLY)
        clearRemoteUpdates()

        contactsPersistenceManager.add(contact.copy(allowedMessageLevel = AllowedMessageLevel.ALL)).get()

        assertThat(contactsPersistenceManager.getRemoteUpdates().get()).apply {
            `as`("Should create a remote update for an modified user")
            containsOnly(AddressBookUpdate.Contact(contact.id, AllowedMessageLevel.ALL))
        }
    }

    @Test
    fun `add should not create a remote update if the message level of an existing user is not modified`() {
        val contact = insertDummyContact(AllowedMessageLevel.GROUP_ONLY)
        clearRemoteUpdates()

        contactsPersistenceManager.add(contact).get()

        assertThat(contactsPersistenceManager.getRemoteUpdates().get()).apply {
            `as`("Should not create a remote update for an unmodified user")
            isEmpty()
        }
    }

    @Test
    fun `add should create a conversation table for an existing user if the new message level is ALL`() {
        val contact = insertDummyContact(AllowedMessageLevel.GROUP_ONLY)
        val newContact = contact.copy(allowedMessageLevel = AllowedMessageLevel.ALL)

        testContactAdd(newContact, true)
    }

    @Test
    fun `add should create a conversation info row for a new contact if the message level is ALL`() {
        val contact = insertDummyContact(AllowedMessageLevel.ALL)

        contactsPersistenceManager.add(contact).get()

        conversationInfoTestUtils.getConversationInfo(contact.id)
        conversationInfoTestUtils.assertConversationInfoExists(contact.id)
    }

    @Test
    fun `add should not create a conversation info row for a new contact if the message level is GROUP_ONLY`() {
        val contact = insertDummyContact(AllowedMessageLevel.GROUP_ONLY)

        contactsPersistenceManager.add(contact).get()

        conversationInfoTestUtils.assertConversationInfoNotExists(contact.id)
    }

    @Test
    fun `add should create a conversation info row for an existing contact if the new message level is ALL`() {
        val contact = insertDummyContact(AllowedMessageLevel.GROUP_ONLY)
        val newContact = contact.copy(allowedMessageLevel = AllowedMessageLevel.ALL)

        contactsPersistenceManager.add(newContact).get()

        conversationInfoTestUtils.assertConversationInfoExists(newContact.id)
    }

    @Test
    fun `addSelf should not generate a remote update`() {
        val contact = contactA
        contactsPersistenceManager.addSelf(contact).get()

        assertTrue(contactsPersistenceManager.getRemoteUpdates().get().isEmpty(), "Remote updates were generated")
    }

    fun testAddContactMulti(existingContacts: Collection<ContactInfo>, newContacts: Collection<ContactInfo>) {
        contactsPersistenceManager.add(existingContacts).get()

        val allContacts = ArrayList<ContactInfo>()
        allContacts.addAll(existingContacts)
        allContacts.addAll(newContacts)

        val added = contactsPersistenceManager.add(allContacts).get()

        assertEquals(newContacts.toSet(), added, "Invalid added contacts")

        val expectedUpdates = allContacts.map { AddressBookUpdate.Contact(it.id, it.allowedMessageLevel) }.sortedBy { it.userId.long }

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
            ContactInfo(UserId(0), "a@a.com", "a", AllowedMessageLevel.ALL, "000-0000", "pubkey"),
            ContactInfo(UserId(1), "b@b.com", "b", AllowedMessageLevel.ALL, "000-0000", "pubkey")
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
    fun `remove should create a corresponding remote update`() {
        val contact = insertDummyContact()

        contactsPersistenceManager.remove(contact.id).get()

        assertThat(contactsPersistenceManager.getRemoteUpdates().get()).apply {
            `as`("Remote updates should contain a GROUP_ONLY entry")
            containsOnly(AddressBookUpdate.Contact(contact.id, AllowedMessageLevel.GROUP_ONLY))
        }
    }

    @Test
    fun `remove should remove the convo log for an existing user`() {
        contactsPersistenceManager.add(contactA).get()

        contactsPersistenceManager.remove(contactA.id).get()

        conversationInfoTestUtils.assertConvTableNotExists(contactA.id)
    }

    //ugh...
    @Test
    fun `remove should remove expiring message entries for an existing user`() {
        val userId = insertDummyContact(AllowedMessageLevel.ALL).id

        val messagePersistenceManager = SQLiteMessagePersistenceManager(persistenceManager)

        val conversationMessageInfo = randomSentConversationMessageInfo()
        messagePersistenceManager.addMessage(userId.toConversationId(), conversationMessageInfo).get()
        messagePersistenceManager.setExpiration(userId.toConversationId(), conversationMessageInfo.info.id, 100).get()

        contactsPersistenceManager.remove(userId).get()

        assertThat(messagePersistenceManager.getMessagesAwaitingExpiration().get()).apply {
            `as`("Expiring message entries should be removed")
            isEmpty()
        }
    }

    @Test
    fun `remove should remove the conversation info for an existing user`() {
        val contact = insertDummyContact(AllowedMessageLevel.ALL)

        contactsPersistenceManager.remove(contact.id).get()

        conversationInfoTestUtils.assertConversationInfoNotExists(contact.id)
    }

    @Test
    fun `remove should return false if no such contact existed`() {
        val wasRemoved = contactsPersistenceManager.remove(randomUserId()).get()

        assertFalse(wasRemoved, "wasRemoved is true")
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
    fun `removeRemoteUpdates should remove only the given updates`() {
        insertDummyContact(AllowedMessageLevel.ALL)
        insertDummyContact(AllowedMessageLevel.ALL)

        val pendingUpdates = contactsPersistenceManager.getRemoteUpdates().get()

        assertEquals(2, pendingUpdates.size, "Invalid number of pending updates")

        val toRemove = pendingUpdates.subList(0, 1)
        val remaining = pendingUpdates.subList(1, 2)

        contactsPersistenceManager.removeRemoteUpdates(toRemove.map { it.userId }).get()

        val got = contactsPersistenceManager.getRemoteUpdates().get()

        assertThat(got).apply {
            `as`("Remote update not removed")
            containsOnlyElementsOf(remaining)
        }
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
    fun `block should create a corresponding remote update`() {
        val contact = insertDummyContact()

        contactsPersistenceManager.block(contact.id).get()

        assertThat(contactsPersistenceManager.getRemoteUpdates().get()).apply {
            `as`("Remote updates should contain a BLOCKED entry")
            containsOnly(AddressBookUpdate.Contact(contact.id, AllowedMessageLevel.BLOCKED))
        }
    }

    @Test
    fun `block should remove a conversation table for an ALL user`() {
        val contact = createDummyContact(AllowedMessageLevel.ALL)

        contactsPersistenceManager.add(contact).get()

        contactsPersistenceManager.block(contact.id).get()

        conversationInfoTestUtils.assertConvTableNotExists(contact.id, "Conversation table not removed")
    }

    @Test
    fun `block should remove the conversation info for an existing user`() {
        val contact = insertDummyContact(AllowedMessageLevel.ALL)

        contactsPersistenceManager.block(contact.id).get()

        conversationInfoTestUtils.assertConversationInfoNotExists(contact.id)
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

    @Test
    fun `unblock should create a remote update when unblocking a user`() {
        val userId = insertDummyContact(AllowedMessageLevel.BLOCKED).id
        clearRemoteUpdates()

        contactsPersistenceManager.unblock(userId).get()

        assertThat(contactsPersistenceManager.getRemoteUpdates().get()).apply {
            `as`("unblock should create a remote update")
            containsOnly(AddressBookUpdate.Contact(userId, AllowedMessageLevel.GROUP_ONLY))
        }
    }

    @Test
    fun `allowAll should set the allowed message level to ALL`() {
        val userId = insertDummyContact(AllowedMessageLevel.GROUP_ONLY).id

        contactsPersistenceManager.allowAll(userId).get()

        val newLevel = fetchContactInfo(userId).allowedMessageLevel

        assertEquals(AllowedMessageLevel.ALL, newLevel, "Message level not updated")
    }

    @Test
    fun `allowAll should create a remote update`() {
        val userId = insertDummyContact(AllowedMessageLevel.GROUP_ONLY).id
        clearRemoteUpdates()

        contactsPersistenceManager.allowAll(userId).get()

        assertThat(contactsPersistenceManager.getRemoteUpdates().get()).apply {
            `as`("allowAll should create a remote update")
            containsOnly(AddressBookUpdate.Contact(userId, AllowedMessageLevel.ALL))
        }
    }

    @Test
    fun `allowAll should create conversation data`() {
        val userId = insertDummyContact(AllowedMessageLevel.GROUP_ONLY).id
        clearRemoteUpdates()

        contactsPersistenceManager.allowAll(userId).get()

        conversationInfoTestUtils.assertConversationInfoExists(userId)
        conversationInfoTestUtils.assertConvTableExists(userId)
    }

    @Test
    fun `when multiple updates to the same contact are performed, keep only the last operation remote update`() {
        val userId = insertDummyContact(AllowedMessageLevel.ALL).id
        contactsPersistenceManager.remove(userId).get()
        contactsPersistenceManager.block(userId).get()

        assertThat(contactsPersistenceManager.getRemoteUpdates().get()).apply {
            `as`("Remote update should only contain the final BLOCKED entry")
            containsOnly(AddressBookUpdate.Contact(userId, AllowedMessageLevel.BLOCKED))
        }
    }

    @Test
    fun `applyDiff should add all given new contacts`() {
        val newContacts = randomContactInfoList()

        contactsPersistenceManager.applyDiff(newContacts, emptyList()).get()

        newContacts.forEach {
            assertNotNull(contactsPersistenceManager.get(it.id), "Missing user")
        }
    }

    @Test
    fun `applyDiff should update message levels for existing contacts`() {
        val userId = insertDummyContact(AllowedMessageLevel.ALL).id
        val updatedMessageLevel = AllowedMessageLevel.GROUP_ONLY

        contactsPersistenceManager.applyDiff(emptyList(), listOf(AddressBookUpdate.Contact(userId, updatedMessageLevel))).get()

        val contactInfo = fetchContactInfo(userId)

        assertEquals(updatedMessageLevel, contactInfo.allowedMessageLevel, "Message level not updated")
    }

    @Test
    fun `applyDiff should create conversation info and log for new contacts with ALL message level`() {
        val newContacts = randomContactInfoList()

        contactsPersistenceManager.applyDiff(newContacts, emptyList()).get()

        newContacts.forEach {
            conversationInfoTestUtils.assertConvTableExists(it.id)
            conversationInfoTestUtils.assertConversationInfoExists(it.id)
        }
    }

    @Test
    fun `applyDiff should create conversation info and log for contacts moved to ALL message level`() {
        val contactInfo = insertDummyContact(AllowedMessageLevel.GROUP_ONLY)
        val update = AddressBookUpdate.Contact(contactInfo.id, AllowedMessageLevel.ALL)

        contactsPersistenceManager.applyDiff(emptyList(), listOf(update)).get()

        conversationInfoTestUtils.assertConvTableExists(contactInfo.id)
        conversationInfoTestUtils.assertConversationInfoExists(contactInfo.id)
    }

    fun testApplyDiffNewContactNoConvo(allowedMessageLevel: AllowedMessageLevel) {
        val newContacts = randomContactInfoList(allowedMessageLevel)

        contactsPersistenceManager.applyDiff(newContacts, emptyList()).get()

        newContacts.forEach {
            conversationInfoTestUtils.assertConvTableNotExists(it.id)
            conversationInfoTestUtils.assertConversationInfoNotExists(it.id)
        }
    }

    @Test
    fun `applyDiff should not create conversation info or log for new contacts with GROUP_ONLY message level`() {
        testApplyDiffNewContactNoConvo(AllowedMessageLevel.GROUP_ONLY)
    }

    fun testApplyDiffUpdatesNoConvo(allowedMessageLevel: AllowedMessageLevel) {
        val contactInfo = insertDummyContact()
        val update = AddressBookUpdate.Contact(contactInfo.id, allowedMessageLevel)

        contactsPersistenceManager.applyDiff(emptyList(), listOf(update)).get()

        conversationInfoTestUtils.assertConvTableNotExists(contactInfo.id)
        conversationInfoTestUtils.assertConversationInfoNotExists(contactInfo.id)
    }

    @Test
    fun `applyDiff should remove convo info and log for contacts moved from ALL to GROUP_ONLY`() {
        testApplyDiffUpdatesNoConvo(AllowedMessageLevel.GROUP_ONLY)
    }

    @Test
    fun `applyDiff should remove convo info and log for contacts moved from ALL to BLOCKED`() {
        testApplyDiffUpdatesNoConvo(AllowedMessageLevel.BLOCKED)
    }

    @Test
    fun `applyDiff should not create remote updates for new contacts`() {
        val newContacts = randomContactInfoList()

        contactsPersistenceManager.applyDiff(newContacts, emptyList()).get()

        assertThat(contactsPersistenceManager.getRemoteUpdates().get()).apply {
            `as`("Remote updates should not be created for new users")
            isEmpty()
        }
    }

    @Test
    fun `applyDiff should create remote updates for updated contacts`() {
        val userId = insertDummyContact(AllowedMessageLevel.ALL).id
        val updatedMessageLevel = AllowedMessageLevel.GROUP_ONLY

        val remoteContactUpdate = AddressBookUpdate.Contact(userId, updatedMessageLevel)
        contactsPersistenceManager.applyDiff(emptyList(), listOf(remoteContactUpdate)).get()

        assertThat(contactsPersistenceManager.getRemoteUpdates().get()).apply {
            `as`("Remote updates should be created for updated users")
            containsOnly(remoteContactUpdate)
        }
    }

    @Test
    fun `get(ids) should return all requested users`() {
        val one = insertDummyContact()
        val two = insertDummyContact()

        val got = contactsPersistenceManager.get(listOf(one.id, two.id)).get()
        assertThat(got).apply {
            `as`("getAll should return all requested users")
            containsOnly(one, two)
        }
    }

    @Test
    fun `addAddressBookHashes should return the final hash`() {
        val hashes = listOf(
            RemoteAddressBookEntry("a250a891b058c5a8c91cebff812a2ab1f866b7f6824d088f580e157e94e7fba9", "78b67c974d4568edb213ae20dac3fc26".unhexify()),
            RemoteAddressBookEntry("e92e18352e6048789e121d27c18d506d1b696690e952a51ab3b45ccb5347261c", "70d6a2405abc2e4c7de27333618a73c7".unhexify())
        )

        val hash = contactsPersistenceManager.addRemoteEntryHashes(hashes).get()

        assertEquals("1159434cef1766bce1b8759b2c8d6d66", hash, "Invalid hash")
    }

    @Test
    fun `addAddressBookHashes should replace existing entries`() {
        val idHash = "e92e18352e6048789e121d27c18d506d1b696690e952a51ab3b45ccb5347261c"

        val hashes = listOf(
            RemoteAddressBookEntry("a250a891b058c5a8c91cebff812a2ab1f866b7f6824d088f580e157e94e7fba9", "78b67c974d4568edb213ae20dac3fc26".unhexify()),
            RemoteAddressBookEntry(idHash, "70d6a2405abc2e4c7de27333618a73c7".unhexify())
        )

        val newHashes = listOf(
            RemoteAddressBookEntry(idHash, "9cc42fbd334c3cba10b13735d3cb24a2".unhexify())
        )

        contactsPersistenceManager.addRemoteEntryHashes(hashes).get()

        val hash = contactsPersistenceManager.addRemoteEntryHashes(newHashes).get()

        assertEquals("e11aed8cac52cd06bc48c49d75dbae6d", hash, "Invalid hash")
    }
}
