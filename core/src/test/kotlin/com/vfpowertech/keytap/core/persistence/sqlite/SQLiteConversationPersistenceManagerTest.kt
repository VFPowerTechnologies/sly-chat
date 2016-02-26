package com.vfpowertech.keytap.core.persistence.sqlite

import com.almworks.sqlite4java.SQLiteException
import com.vfpowertech.keytap.core.persistence.InvalidConversationException
import com.vfpowertech.keytap.core.persistence.MessageInfo
import com.vfpowertech.keytap.core.test.withTimeAs
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import java.util.*
import kotlin.test.*

class SQLiteConversationPersistenceManagerTest {
    companion object {
        @JvmStatic
        @BeforeClass
        fun loadLibrary() {
            SQLitePreKeyPersistenceManager::class.java.loadSQLiteLibraryFromResources()
        }
    }

    val contact = "a@a.com"
    val testMessage = "test message"

    lateinit var persistenceManager: SQLitePersistenceManager
    lateinit var conversationPersistenceManager: SQLiteConversationPersistenceManager

    @Before
    fun before() {
        persistenceManager = SQLitePersistenceManager(null, ByteArray(0), null)
        persistenceManager.init()
        conversationPersistenceManager = SQLiteConversationPersistenceManager(persistenceManager)
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

    fun assertTableNotExists(tableName: String) {
        assert(!doesTableExist(tableName))
    }

    fun createConvosFor(vararg contacts: String): Array<out String> {
        contacts.forEach { conversationPersistenceManager.createNewConversation(it).get() }
        return contacts
    }

    fun addMessage(contact: String, isSent: Boolean, message: String, ttl: Long): MessageInfo =
        conversationPersistenceManager.addMessage(contact, isSent, message, ttl).get()

    @Test
    fun `createConversation should create a conversation table for the given user`() {
        createConvosFor(contact)
        assertTableExists("conv_$contact")
    }

    @Test
    fun `createConversation should not error if a conversation table already exists`() {
        createConvosFor(contact)
        createConvosFor(contact)
    }

    @Test
    fun `createConversation should not fail when a username contains a backtick`() {
        conversationPersistenceManager.createNewConversation("`a").get()
    }

    @Test
    fun `deleteConversation should delete the user table`() {
        createConvosFor(contact)
        conversationPersistenceManager.deleteConversation(contact).get()
        assertTableNotExists("conv_$contact.com")
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
        assertFalse(messageInfo.isRead)
    }

    @Test
    fun `markMessageAsDelivered should update isDelivered and timestamp fields`() {
        createConvosFor(contact)

        val sentMessageInfo = addMessage(contact, true, testMessage, 0)

        val expectedTimestamp = sentMessageInfo.timestamp+10

        val updatedMessageInfo = withTimeAs(expectedTimestamp) {
            conversationPersistenceManager.markMessageAsDelivered(contact, sentMessageInfo.id).get()
        }

        assertEquals(expectedTimestamp, updatedMessageInfo.timestamp)
        assertTrue(updatedMessageInfo.isDelivered)
    }

    @Test
    fun `getAllConversations should return an empty list if no conversations are available`() {
        assertTrue(conversationPersistenceManager.getAllConversations().get().isEmpty())
    }

    @Test
    fun `getAllConversations should return all available conversations`() {
        createConvosFor("a", "b")

        val got = conversationPersistenceManager.getAllConversations().get()

        assertEquals(2, got.size)
    }

    @Test
    fun `getAllConversations should return a last message field if messages are available`() {
        createConvosFor("a", "b")

        addMessage("a", false, "ignored", 0)
        addMessage("a", false, testMessage, 0)

        val got = conversationPersistenceManager.getAllConversations().get().sortedBy { it.contact }

        assertEquals(testMessage, got[0].lastMessage)
        assertNull(got[1].lastMessage)
    }

    @Test
    fun `getConversation should return a conversation if it exists`() {
        createConvosFor(contact)

        conversationPersistenceManager.getConversationInfo(contact).get()
    }

    @Test
    fun `getConversation should include unread message counts in a conversation`() {
        createConvosFor(contact)

        val before = conversationPersistenceManager.getConversationInfo(contact).get()
        assertEquals(0, before.unreadMessageCount)

        addMessage(contact, false, testMessage, 0)

        val after = conversationPersistenceManager.getConversationInfo(contact).get()
        assertEquals(1, after.unreadMessageCount)
    }

    @Test
    fun `getConversation should throw InvalidConversationException if the given conversation doesn't exist`() {
        assertFailsWith(InvalidConversationException::class) {
            conversationPersistenceManager.getConversationInfo(contact).get()
        }
    }

    @Test
    fun `markConversationAsRead should mark all unread messages as read`() {
        createConvosFor(contact)

        for (i in 0..2)
            addMessage(contact, false, testMessage, 0)

        conversationPersistenceManager.markConversationAsRead(contact).get()
        
        val got = conversationPersistenceManager.getConversationInfo(contact).get()
        assertEquals(0, got.unreadMessageCount)
    }

    @Test
    fun `markConversationAsRead should throw InvalidConversationException if the given conversation doesn't exist`() {
        assertFailsWith(InvalidConversationException::class) {
            conversationPersistenceManager.markConversationAsRead(contact).get()
        }
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

        val got = conversationPersistenceManager.getLastMessages(contact, start, count).get()

        assertEquals(count, got.size)
        assertEquals(expected, got)
    }

    @Test
    fun `getUndeliveredMessages should return all undelivered messages`() {
        createConvosFor(contact)

        addMessage(contact, false, testMessage, 0)

        val count = 2

        val messages = ArrayList<MessageInfo>()
        for (i in 0..count-1)
            messages.add(addMessage(contact, true, testMessage, 0))

        val undelivered = conversationPersistenceManager.getUndeliveredMessages(contact).get()
        assertEquals(count, undelivered.size)

        undelivered.forEach { assertFalse(it.isDelivered) }
    }
}