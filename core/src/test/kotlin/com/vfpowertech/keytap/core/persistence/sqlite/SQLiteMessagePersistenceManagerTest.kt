package com.vfpowertech.keytap.core.persistence.sqlite

import com.almworks.sqlite4java.SQLiteException
import com.vfpowertech.keytap.core.UserId
import com.vfpowertech.keytap.core.persistence.MessageInfo
import com.vfpowertech.keytap.core.test.withTimeAs
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
                connection.prepare("INSERT INTO conversation_info (contact_id, unread_count, last_message) VALUES (?, 0, NULL)").use { stmt ->
                    stmt.bind(1, contact.long)
                    stmt.step()
                }
            }
        }
        return contacts
    }

    fun addMessage(userId: UserId, isSent: Boolean, message: String, ttl: Long): MessageInfo =
        messagePersistenceManager.addMessage(userId, isSent, message, ttl).get()

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
    fun `addMessage should add a valid received message`() {
        createConvosFor(contact)

        val messageInfo = addMessage(contact, false, testMessage, 0)
        assertEquals(testMessage, messageInfo.message)
        assertFalse(messageInfo.isSent)
    }

    @Test
    fun `markMessageAsDelivered should update isDelivered and timestamp fields`() {
        createConvosFor(contact)

        val sentMessageInfo = addMessage(contact, true, testMessage, 0)

        val expectedTimestamp = sentMessageInfo.timestamp+10

        val updatedMessageInfo = withTimeAs(expectedTimestamp) {
            messagePersistenceManager.markMessageAsDelivered(contact, sentMessageInfo.id).get()
        }

        assertEquals(expectedTimestamp, updatedMessageInfo.timestamp)
        assertTrue(updatedMessageInfo.isDelivered)
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
        val contact2 = UserId(contact.long +1)
        val contact3 = UserId(contact2.long +1)
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
}