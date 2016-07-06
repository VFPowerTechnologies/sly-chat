package io.slychat.messenger.core.persistence.sqlite

import com.almworks.sqlite4java.SQLiteException
import io.slychat.messenger.core.SlyAddress
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.currentTimestamp
import io.slychat.messenger.core.persistence.MessageInfo
import io.slychat.messenger.core.persistence.Package
import io.slychat.messenger.core.persistence.PackageId
import io.slychat.messenger.core.randomUUID
import io.slychat.messenger.testutils.withTimeAs
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private data class LastConversationInfo(val unreadCount: Int, val lastMessage: String?, val lastTimestamp: Long?)

class SQLiteMessagePersistenceManagerTest {
    companion object {
        @JvmStatic
        @BeforeClass
        fun loadLibrary() {
            SQLitePreKeyPersistenceManager::class.java.loadSQLiteLibraryFromResources()
        }
    }

    val contact = UserId(0)
    val testMessage = "test message"

    lateinit var persistenceManager: SQLitePersistenceManager
    lateinit var contactsPersistenceManager: SQLiteContactsPersistenceManager
    lateinit var messagePersistenceManager: SQLiteMessagePersistenceManager

    @Before
    fun before() {
        persistenceManager = SQLitePersistenceManager(null, null, null)
        persistenceManager.init()
        contactsPersistenceManager = SQLiteContactsPersistenceManager(persistenceManager)
        messagePersistenceManager = SQLiteMessagePersistenceManager(persistenceManager)
    }

    @After
    fun after() {
        persistenceManager.shutdown()
    }

    //TODO escape ``
    fun doesTableExist(tableName: String): Boolean {
        try {
            persistenceManager.runQuery { connection ->
                connection.prepare("SELECT * FROM `$tableName`").use { stmt ->
                    stmt.step()
                }
            }.get()
        }
        catch (e: SQLiteException) {
           if ("no such table:" in e.message.toString())
               return false
            throw e
        }
        return true
    }

    fun assertTableExists(tableName: String) {
        assert(doesTableExist(tableName))
    }

    private fun getLastConversationInfo(contact: UserId): LastConversationInfo? {
        return persistenceManager.syncRunQuery { connection ->
            connection.prepare("SELECT unread_count, last_message, last_timestamp FROM conversation_info WHERE contact_id=?").use { stmt ->
                stmt.bind(1, contact.long)
                if (!stmt.step())
                    null
                else {
                    val lastMessage = if (stmt.columnNull(1))
                        null
                    else
                        stmt.columnString(1)

                    val timestamp = if (stmt.columnNull(2))
                        null
                    else
                        stmt.columnLong(2)

                    LastConversationInfo(stmt.columnInt(0), lastMessage, timestamp)
                }
            }
        }
    }

    fun createConvosFor(vararg contacts: UserId): Array<out UserId> {
        contacts.forEach { contact ->
            persistenceManager.syncRunQuery { ConversationTable.create(it, contact) }
            //XXX this is used by SQLiteContactsPersistenceManager, so should probably find a way to share this code
            persistenceManager.syncRunQuery { connection ->
                connection.withPrepared("INSERT INTO contacts (id, email, name, is_pending, public_key) VALUES (?, ?, 'Name', 0, X'aa')") { stmt ->
                    stmt.bind(1, contact.long)
                    stmt.bind(2, "${contact.long}@a.com")
                    stmt.step()
                }

                connection.withPrepared("INSERT INTO conversation_info (contact_id, unread_count, last_message) VALUES (?, 0, NULL)") { stmt ->
                    stmt.bind(1, contact.long)
                    stmt.step()
                }
            }
        }
        return contacts
    }

    fun addMessage(userId: UserId, isSent: Boolean, message: String, ttl: Long): MessageInfo {
        val messageInfo = if (isSent)
            MessageInfo.newSent(message, ttl)
        else
             MessageInfo.newReceived(message, currentTimestamp(), ttl)

        return messagePersistenceManager.addMessage(userId, messageInfo).get()
    }

    @Test
    fun `createConversation should create a conversation table for the given user`() {
        createConvosFor(contact)
        assertTableExists(ConversationTable.getTablenameForContact(contact))
    }

    @Test
    fun `createConversation should not error if a conversation table already exists`() {
        createConvosFor(contact)
        assertTableExists(ConversationTable.getTablenameForContact(contact))
    }

    @Test
    fun `addMessage should add a valid sent message`() {
        createConvosFor(contact)
        val ttl = 5L

        val messageInfo = addMessage(contact, true, testMessage, ttl)
        assertEquals(testMessage, messageInfo.message)
        assertEquals(ttl, messageInfo.ttl)
        assertTrue(messageInfo.isSent)
        assertFalse(messageInfo.isDelivered)
    }

    @Test
    fun `addMessage should remove a corresponding queued message if it exists`() {
        val userId = UserId(1)
        createConvosFor(userId)

        val messageId = randomUUID()
        val address = SlyAddress(userId, 1)
        val pkg = Package(PackageId(address, messageId), currentTimestamp(), "payload")

        messagePersistenceManager.addToQueue(pkg).get()

        val messageInfo = MessageInfo.newReceived(messageId, "message", currentTimestamp(), currentTimestamp(), 0)

        messagePersistenceManager.addMessage(address.id, messageInfo).get()

        val queued = messagePersistenceManager.getQueuedPackages(userId).get()
        assertTrue(queued.isEmpty(), "Queued packages not empty")
    }

    @Test
    fun `addMessages should remove all corresponding queued packages if they exist`() {
        val userId = UserId(1)
        val address = SlyAddress(userId, 1)
        createConvosFor(userId)

        fun newReceivedMessage(i: Int): MessageInfo =
            MessageInfo.newReceived("message $i", currentTimestamp())

        val with = (0..1).map { newReceivedMessage(it) }
        val withOut = (2..3).map { newReceivedMessage(it) }

        val messages = ArrayList<MessageInfo>()
        messages.addAll(withOut)
        messages.addAll(with)

        val packages = with.map { Package(PackageId(address, it.id), currentTimestamp(), it.message) }

        messagePersistenceManager.addToQueue(packages).get()

        messagePersistenceManager.addMessages(userId, messages).get()

        val queued = messagePersistenceManager.getQueuedPackages(userId).get()

        assertTrue(queued.isEmpty(), "Queued packages not empty: $queued")
    }

    @Test
    fun `addMessages should do nothing when given an empty list`() {
        assertTrue(messagePersistenceManager.addMessages(UserId(0), listOf()).get().isEmpty(), "List not empty")
    }

    @Test
    fun `addSentMessage should add a valid received message`() {
        createConvosFor(contact)

        val messageInfo = addMessage(contact, false, testMessage, 0)
        assertEquals(testMessage, messageInfo.message)
        assertFalse(messageInfo.isSent)
    }

    @Test
    fun `addMessageInfo should update conversation info`() {
        createConvosFor(contact)

        val messageInfo = MessageInfo.newSent("message", 0)
        messagePersistenceManager.addMessage(contact, messageInfo).get()
        val lastConversationInfo = getLastConversationInfo(contact) ?: throw AssertionError("No last conversation info")

        assertEquals(messageInfo.timestamp, lastConversationInfo.lastTimestamp, "Timestamp wasn't updated")
        assertEquals(messageInfo.message, lastConversationInfo.lastMessage, "Message wasn't updated")
    }

    @Test
    fun `markMessageAsDelivered should update isDelivered and receivedTimestamp fields`() {
        createConvosFor(contact)

        val sentMessageInfo = addMessage(contact, true, testMessage, 0)

        val expectedTimestamp = sentMessageInfo.timestamp+10

        val updatedMessageInfo = withTimeAs(expectedTimestamp) {
            messagePersistenceManager.markMessageAsDelivered(contact, sentMessageInfo.id).get()
        }

        assertEquals(expectedTimestamp, updatedMessageInfo.receivedTimestamp)
        assertTrue(updatedMessageInfo.isDelivered)
    }

    @Test
    fun `addMessages should add all messages`() {
        val user1 = UserId(1)
        val base = currentTimestamp()
        val user1Messages = listOf(
            MessageInfo.newReceived("message 1", base, 0),
            MessageInfo.newReceived("message 2", base + 1000, 0)
        )

        createConvosFor(user1)

        val messageInfoMap = messagePersistenceManager.addMessages(user1, user1Messages).get()

        assertEquals(user1Messages, messageInfoMap, "MessageInfo lists don't match")
    }

    @Test
    fun `getLastMessages should return the given message range in reverse order`() {
        createConvosFor(contact)

        val messages = ArrayList<MessageInfo>()
        for (i in 0..9)
            messages.add(addMessage(contact, false, testMessage, 0))

        val start = 4
        val count = 4
        val expected = messages.reversed().subList(start, start+count)

        val got = messagePersistenceManager.getLastMessages(contact, start, count).get()

        assertEquals(count, got.size)
        assertEquals(expected, got)
    }

    @Test
    fun `getUndeliveredMessages should return all undelivered messages`() {
        val contact2 = UserId(contact.long + 1)
        val contact3 = UserId(contact2.long + 1)
        createConvosFor(contact, contact2, contact3)

        val contacts = listOf(contact, contact2)

        val expected = contacts.map { c ->
            val messages = (0..6).map { i ->
                val isSent = (i % 2) == 0
                addMessage(c, isSent, "Message $i", 0)
            }.filter { !it.isDelivered }

            c to messages
        }.toMap()

        val undelivered = messagePersistenceManager.getUndeliveredMessages().get()
        val gotContacts = undelivered.keys.sortedBy { it.long }

        assertEquals(contacts, gotContacts)

        assertEquals(expected, undelivered)
    }

    private fun assertEmptyLastConversationInfo(lastConversationInfo: LastConversationInfo) {
        assertEquals(0, lastConversationInfo.unreadCount, "Invalid unreadCount")
        assertNull(lastConversationInfo.lastMessage, "lastMessage isn't null")
        assertNull(lastConversationInfo.lastTimestamp, "lastTimestamp isn't null")
    }

    @Test
    fun `deleteAllMessages should remove all messages and update the conversion info for the corresponding user`() {
        createConvosFor(contact)
        addMessage(contact, false, "received", 0)
        addMessage(contact, true, "sent", 0)

        messagePersistenceManager.deleteAllMessages(contact).get()
        assertEquals(0, messagePersistenceManager.getLastMessages(contact, 0, 100).get().size, "Should not have any messages")

        val lastConversationInfo = getLastConversationInfo(contact) ?: throw AssertionError("No last conversation info")

        assertEmptyLastConversationInfo(lastConversationInfo)
    }

    @Test
    fun `deleteMessages should do nothing if the message list is empty`() {
        createConvosFor(contact)

        addMessage(contact, false, "received", 0)
        addMessage(contact, true, "sent", 0)

        messagePersistenceManager.deleteMessages(contact, listOf()).get()

        assertEquals(2, messagePersistenceManager.getLastMessages(contact, 0, 100).get().size, "Message count doesn't match")
    }

    @Test
    fun `deleteMessages should remove the specified messages and update conversation info for the corresponding user (empty result)`() {
        createConvosFor(contact)

        val messageInfo = addMessage(contact, false, "received", 0)

        messagePersistenceManager.deleteMessages(contact, listOf(messageInfo.id)).get()

        val lastConversationInfo = getLastConversationInfo(contact) ?: throw AssertionError("No last conversation info")

        assertEmptyLastConversationInfo(lastConversationInfo)
    }

    @Test
    fun `deleteMessages should remove the specified messages and update conversation info for the corresponding user (remaining result)`() {
        createConvosFor(contact)

        val keep = ArrayList<MessageInfo>()
        val remove = ArrayList<MessageInfo>()

        for (i in 0..8) {
            val list = if (i % 2 == 0) remove else keep

            list.add(addMessage(contact, false, "received $i", 0))
            list.add(addMessage(contact, true, "sent $i", 0))
        }

        messagePersistenceManager.deleteMessages(contact, remove.map { it.id }).get()

        //match returned order
        val keepSorted = keep.reversed()
        val remainingSorted = messagePersistenceManager.getLastMessages(contact, 0, 100).get()

        assertEquals(keepSorted, remainingSorted, "Invalid remaining messages")

        val lastConversationInfo = getLastConversationInfo(contact) ?: throw AssertionError("No last conversation info")

        val lastMessage = keepSorted.first()

        //can't be done (see impl notes)
        //val expectedUnread = keepSorted.filter { it.isSent == false }.size

        //assertEquals(expectedUnread, lastConversationInfo.unreadCount, "Invalid unreadCount")
        assertEquals(lastMessage.message, lastConversationInfo.lastMessage, "lastMessage doesn't match")
        assertEquals(lastMessage.timestamp, lastConversationInfo.lastTimestamp, "lastTimestamp doesn't match")
    }

    private fun queuedPackageFromInt(address: SlyAddress, i: Int): Package {
        return Package(
            PackageId(address, "$i"),
            currentTimestamp() + (i * 10),
            "message $i"
        )
    }

    @Test
    fun `addToQueue should store the given packages`() {
        val address = SlyAddress(UserId(1), 1)
        val queuedMessages = (0..1).map { queuedPackageFromInt(address, it) }

        messagePersistenceManager.addToQueue(queuedMessages).get()
        val got = messagePersistenceManager.getQueuedPackages().get()

        val expected = queuedMessages.sortedBy { it.timestamp }
        val gotSorted = got.sortedBy { it.timestamp }

        assertEquals(queuedMessages.size, gotSorted.size, "Invalid number of messages")
        assertEquals(expected, gotSorted, "Invalid messages")
    }

    @Test
    fun `getQueuedPackages(UserId) should only return packages for the given user`() {
        val address1 = SlyAddress(UserId(1), 1)
        val address2 = SlyAddress(UserId(2), 1)
        val queuedPackages1 = (0..1).map { queuedPackageFromInt(address1, it) }
        val queuedPackages2 = (0..1).map { queuedPackageFromInt(address2, it) }

        messagePersistenceManager.addToQueue(queuedPackages1).get()
        messagePersistenceManager.addToQueue(queuedPackages2).get()

        val got = messagePersistenceManager.getQueuedPackages(address1.id).get()

        assertEquals(queuedPackages1, got, "Packages don't match")
    }

    @Test
    fun `getQueuedPackages(Set) should only return packages for the given users`() {
        val addresses = (0..2).map { SlyAddress(UserId(it.toLong()), 1) }

        val queuedPackages = addresses.map { address ->
            (0..1).map { queuedPackageFromInt(address, it) }
        }

        messagePersistenceManager.addToQueue(queuedPackages.flatten()).get()

        val interestedUsers = addresses.subList(0, 2).map { it.id }.toSet()

        val packages = messagePersistenceManager.getQueuedPackages(interestedUsers).get()

        assertTrue(packages.all { it.id.address.id in interestedUsers }, "Invalid package list")
    }

    @Test
    fun `removeFromQueue should remove the given packages`() {
        val address = SlyAddress(UserId(1), 1)
        val queuedPackages = (0..3).map { queuedPackageFromInt(address, it) }

        val toRemove = queuedPackages.subList(0, 2)
        val toKeep = queuedPackages.subList(2, 4)

        messagePersistenceManager.addToQueue(queuedPackages).get()
        messagePersistenceManager.removeFromQueue(address.id, toRemove.map { it.id.messageId }).get()

        val remaining = messagePersistenceManager.getQueuedPackages().get()

        assertEquals(toKeep.size, remaining.size, "Invalid number of messages")

        val remainingSorted = remaining.sortedBy { it.timestamp }
        val toKeepSorted = toKeep.sortedBy { it.timestamp }

        assertEquals(toKeepSorted, remainingSorted, "Invalid messages")
    }

    @Test
    fun `removeFromQueue(PackageId) should remove the given packages`() {
        val address = SlyAddress(UserId(1), 1)
        val queuedPackages = (0..3).map { queuedPackageFromInt(address, it) }

        val toRemove = queuedPackages.subList(0, 2)
        val toKeep = queuedPackages.subList(2, 4)

        messagePersistenceManager.addToQueue(queuedPackages).get()
        messagePersistenceManager.removeFromQueue(toRemove.map { it.id }).get()

        val remaining = messagePersistenceManager.getQueuedPackages().get()

        assertEquals(toKeep.size, remaining.size, "Invalid number of messages")

        val remainingSorted = remaining.sortedBy { it.timestamp }
        val toKeepSorted = toKeep.sortedBy { it.timestamp }

        assertEquals(toKeepSorted, remainingSorted, "Invalid messages")
    }

    @Test
    fun `removeFromQueue(UserId) should remove all packages for a user`() {
        val address = SlyAddress(UserId(1), 1)
        val queuedPackages = (0..3).map { queuedPackageFromInt(address, it) }

        messagePersistenceManager.addToQueue(queuedPackages).get()

        messagePersistenceManager.removeFromQueue(address.id).get()

        val queued = messagePersistenceManager.getQueuedPackages(address.id).get()

        assertTrue(queued.isEmpty(), "Packages not deleted")
    }

    @Test
    fun `removeFromQueue(Set) should remove all packages for the given users`() {
        val address1 = SlyAddress(UserId(1), 1)
        val address2 = SlyAddress(UserId(2), 1)
        val address3 = SlyAddress(UserId(3), 1)
        val queuedPackages1 = (0..1).map { queuedPackageFromInt(address1, it) }
        val queuedPackages2 = (0..1).map { queuedPackageFromInt(address2, it) }
        val queuedPackages3 = (0..1).map { queuedPackageFromInt(address3, it) }

        messagePersistenceManager.addToQueue(queuedPackages1).get()
        messagePersistenceManager.addToQueue(queuedPackages2).get()
        messagePersistenceManager.addToQueue(queuedPackages3).get()

        messagePersistenceManager.removeFromQueue(setOf(address1.id, address2.id)).get()

        val queued = messagePersistenceManager.getQueuedPackages().get()

        assertEquals(queuedPackages3, queued, "Invalid package list")
    }
}