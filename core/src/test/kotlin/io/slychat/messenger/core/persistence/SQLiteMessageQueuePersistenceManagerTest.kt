package io.slychat.messenger.core.persistence

import io.slychat.messenger.core.*
import io.slychat.messenger.core.crypto.randomMessageId
import io.slychat.messenger.core.persistence.sqlite.SQLitePersistenceManager
import io.slychat.messenger.core.persistence.sqlite.loadSQLiteLibraryFromResources
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import java.util.*
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

    @Before
    fun before() {
        persistenceManager = SQLitePersistenceManager(null, null, null)
        persistenceManager.init()
        messageQueuePersistenceManager = SQLiteMessageQueuePersistenceManager(persistenceManager)
    }

    @After
    fun after() {
        persistenceManager.shutdown()
    }

    @Test
    fun `add(single) should add a new message`() {
        val qm = randomQueuedMessage()

        messageQueuePersistenceManager.add(qm).get()

        val got = messageQueuePersistenceManager.get(qm.metadata.userId, qm.metadata.messageId).get()

        assertEquals(qm, got, "Invalid queued message")
    }

    @Test
    fun `add(multi) should add all the given new messages`() {
        val qms = randomQueuedMessages()

        messageQueuePersistenceManager.add(qms).get()

        qms.forEach {
            val got = messageQueuePersistenceManager.get(it.metadata.userId, it.metadata.messageId).get()
            assertEquals(it, got, "Invalid queued message")
        }
    }

    @Test
    fun `remove should remove an existing message`() {
        val qm = randomQueuedMessage()
        messageQueuePersistenceManager.add(qm).get()

        assertTrue(messageQueuePersistenceManager.remove(qm.metadata.userId, qm.metadata.messageId).get(), "Message not removed")
    }

    @Test
    fun `remove should do nothing if a message does not exist`() {
        assertFalse(messageQueuePersistenceManager.remove(randomUserId(), randomMessageId()).get(), "Message not removed")
    }

    @Test
    fun `getUndelivered should return all previously added messages`() {
        val qms = randomQueuedMessages()

        messageQueuePersistenceManager.add(qms).get()

        val undelivered = messageQueuePersistenceManager.getUndelivered().get()

        assertThat(undelivered)
            .containsOnlyElementsOf(qms)
            .`as`("Undelievered messages")

        val sorted = undelivered.sortedBy { it.timestamp }

        assertEquals(sorted, undelivered, "Undelivered messages not properly sorted")
    }

    @Test
    fun `getUndelivered should return all previously added messages in descending order by timestamp`() {
        val timestampBase = currentTimestamp()

        val qms = (0..10).mapTo(ArrayList()) {
            QueuedMessage(
                randomTextSingleMetadata(),
                timestampBase+it,
                randomSerializedMessage()
            )
        }

        //we attempt to randomize the list a bit so that insertion order doesn't reflect timestamp order
        Collections.shuffle(qms)

        messageQueuePersistenceManager.add(qms).get()

        val undelivered = messageQueuePersistenceManager.getUndelivered().get()

        assertFalse(undelivered.isEmpty(), "No messages returned")

        //displaying the timestamps only in case of failure is nicer
        val sorted = undelivered.map { it.timestamp }.sorted()

        assertEquals(sorted, undelivered.map { it.timestamp }, "Undelivered messages not properly sorted")
    }

    @Test
    fun `getUndelivered should return nothing if no messages were previously added`() {
        val undelivered = messageQueuePersistenceManager.getUndelivered().get()

        assertThat(undelivered)
            .`as`("Undelievered messages")
            .isEmpty()
    }
}