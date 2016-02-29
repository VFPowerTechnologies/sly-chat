package com.vfpowertech.keytap.core.persistence.sqlite

import com.almworks.sqlite4java.SQLiteException
import com.vfpowertech.keytap.core.persistence.MessageInfo
import com.vfpowertech.keytap.core.test.withTimeAs
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
    lateinit var contactsPersistenceManager: SQLiteContactsPersistenceManager
    lateinit var conversationPersistenceManager: SQLiteConversationPersistenceManager

    @Before
    fun before() {
        persistenceManager = SQLitePersistenceManager(null, ByteArray(0), null)
        persistenceManager.init()
        contactsPersistenceManager = SQLiteContactsPersistenceManager(persistenceManager)
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

    fun createConvosFor(vararg contacts: String): Array<out String> {
        contacts.forEach { contact -> persistenceManager.runQuery { ConversationTable.create(it, contact) } }
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
            conversationPersistenceManager.markMessageAsDelivered(contact, sentMessageInfo.id).get()
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