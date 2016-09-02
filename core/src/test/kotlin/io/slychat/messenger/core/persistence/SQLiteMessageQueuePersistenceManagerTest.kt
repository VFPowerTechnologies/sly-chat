package io.slychat.messenger.core.persistence

import io.slychat.messenger.core.crypto.randomMessageId
import io.slychat.messenger.core.persistence.sqlite.SQLiteContactsPersistenceManager
import io.slychat.messenger.core.persistence.sqlite.SQLitePersistenceManager
import io.slychat.messenger.core.persistence.sqlite.loadSQLiteLibraryFromResources
import io.slychat.messenger.core.randomContactInfo
import io.slychat.messenger.core.randomSenderMessageEntries
import io.slychat.messenger.core.randomSenderMessageEntry
import io.slychat.messenger.core.randomUserId
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SQLiteMessageQueuePersistenceManagerTest {
    companion object {
        @JvmStatic
        @BeforeClass
        fun loadLibrary() {
            SQLiteMessageQueuePersistenceManagerTest::class.java.loadSQLiteLibraryFromResources()
        }
    }

    lateinit var persistenceManager: SQLitePersistenceManager
    lateinit var messageQueuePersistenceManager: SQLiteMessageQueuePersistenceManager
    lateinit var contactsPersistenceManager: SQLiteContactsPersistenceManager

    @Before
    fun before() {
        persistenceManager = SQLitePersistenceManager(null, null, null)
        persistenceManager.init()
        messageQueuePersistenceManager = SQLiteMessageQueuePersistenceManager(persistenceManager)
        contactsPersistenceManager = SQLiteContactsPersistenceManager(persistenceManager)
    }

    fun addContacts(entry: SenderMessageEntry) {
        addContacts(listOf(entry))
    }

    fun addContacts(entries: Collection<SenderMessageEntry>) {
        val userIds = entries.map { randomContactInfo().copy(id = it.metadata.userId) }
        contactsPersistenceManager.add(userIds).get()
    }


    @After
    fun after() {
        persistenceManager.shutdown()
    }

    fun randomMessageEntry(): SenderMessageEntry {
        val entry = randomSenderMessageEntry()
        addContacts(entry)
        return entry
    }

    fun randomMessageEntries(n: Int = 2): List<SenderMessageEntry> {
        val entries = randomSenderMessageEntries(n)
        addContacts(entries)
        return entries
    }

    @Test
    fun `add(single) should add a new message`() {
        val entry = randomMessageEntry()

        val qm = messageQueuePersistenceManager.add(entry).get()

        val got = messageQueuePersistenceManager.get(qm.metadata.userId, qm.metadata.messageId).get()

        assertEquals(qm, got, "Invalid queued message")
    }

    @Test
    fun `add(multi) should add all the given new messages`() {
        val entries = randomMessageEntries()

        val qms = messageQueuePersistenceManager.add(entries).get()

        qms.forEach {
            val got = messageQueuePersistenceManager.get(it.metadata.userId, it.metadata.messageId).get()
            assertEquals(it, got, "Invalid queued message")
        }
    }

    @Test
    fun `remove should remove an existing message`() {
        val entry = randomMessageEntry()
        val qm = messageQueuePersistenceManager.add(entry).get()

        assertTrue(messageQueuePersistenceManager.remove(qm.metadata.userId, qm.metadata.messageId).get(), "Message not removed")
    }

    @Test
    fun `remove should do nothing if a message does not exist`() {
        assertFalse(messageQueuePersistenceManager.remove(randomUserId(), randomMessageId()).get(), "Message not removed")
    }

    @Test
    fun `getUndelivered should return all previously added messages`() {
        val entries = randomMessageEntries()

        val qms = messageQueuePersistenceManager.add(entries).get()

        val undelivered = messageQueuePersistenceManager.getUndelivered().get()

        assertThat(undelivered)
            .containsOnlyElementsOf(qms)
            .`as`("Undelievered messages")

        val sorted = undelivered.sortedBy { it.id }

        assertEquals(sorted, undelivered, "Undelivered messages not properly sorted")
    }

    @Test
    fun `getUndelivered should return all previously added messages in ascending order by id`() {
        val entries = randomMessageEntries(10)

        val qms = messageQueuePersistenceManager.add(entries).get()

        //make sure they're returned in order as well
        val sortedMetadata = qms.map { it.metadata }

        assertThat(sortedMetadata).apply {
            `as`("Returned values should be in order")
            containsExactlyElementsOf(entries.map { it.metadata })
        }

        val undelivered = messageQueuePersistenceManager.getUndelivered().get()

        assertFalse(undelivered.isEmpty(), "No messages returned")

        //displaying the id only in case of failure is nicer
        val sorted = undelivered.map { it.id }.sorted()

        assertEquals(sorted, undelivered.map { it.id }, "Undelivered messages not properly sorted")
    }

    @Test
    fun `getUndelivered should return nothing if no messages were previously added`() {
        val undelivered = messageQueuePersistenceManager.getUndelivered().get()

        assertThat(undelivered)
            .`as`("Undelievered messages")
            .isEmpty()
    }
}