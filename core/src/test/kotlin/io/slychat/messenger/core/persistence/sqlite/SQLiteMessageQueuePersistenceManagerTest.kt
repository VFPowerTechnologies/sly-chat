package io.slychat.messenger.core.persistence.sqlite

import io.slychat.messenger.core.*
import io.slychat.messenger.core.crypto.randomMessageId
import io.slychat.messenger.core.persistence.MessageCategory
import io.slychat.messenger.core.persistence.MessageMetadata
import io.slychat.messenger.core.persistence.SenderMessageEntry
import io.slychat.messenger.core.persistence.toConversationId
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SQLiteMessageQueuePersistenceManagerTest : GroupPersistenceManagerTestUtils {
    companion object {
        @JvmStatic
        @BeforeClass
        fun loadLibrary() {
            SQLiteMessageQueuePersistenceManagerTest::class.java.loadSQLiteLibraryFromResources()
        }
    }

    lateinit var persistenceManager: SQLitePersistenceManager
    lateinit var messageQueuePersistenceManager: SQLiteMessageQueuePersistenceManager
    lateinit override var contactsPersistenceManager: SQLiteContactsPersistenceManager
    lateinit override var groupPersistenceManager: SQLiteGroupPersistenceManager

    @Before
    fun before() {
        persistenceManager = SQLitePersistenceManager(null, null)
        persistenceManager.init()
        messageQueuePersistenceManager = SQLiteMessageQueuePersistenceManager(persistenceManager)
        contactsPersistenceManager = SQLiteContactsPersistenceManager(persistenceManager)
        groupPersistenceManager = SQLiteGroupPersistenceManager(persistenceManager)
    }

    fun addContacts(entry: SenderMessageEntry) {
        addContacts(listOf(entry))
    }

    fun addContacts(entries: Collection<SenderMessageEntry>) {
        val contactInfo = entries.map { randomContactInfo().copy(id = it.metadata.userId) }
        contactsPersistenceManager.add(contactInfo).get()
    }

    fun addContact(userId: UserId) {
        val contactInfo = randomContactInfo().copy(id = userId)
        contactsPersistenceManager.add(contactInfo).get()
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

        messageQueuePersistenceManager.add(entry).get()

        val got = messageQueuePersistenceManager.get(entry.metadata.userId, entry.metadata.messageId).get()

        assertEquals(entry, got, "Invalid message entry")
    }

    @Test
    fun `add(multi) should add all the given new messages`() {
        val entries = randomMessageEntries()

        messageQueuePersistenceManager.add(entries).get()

        entries.forEach {
            val got = messageQueuePersistenceManager.get(it.metadata.userId, it.metadata.messageId).get()
            assertEquals(it, got, "Invalid message entry")
        }
    }

    @Test
    fun `remove should remove an existing message`() {
        val entry = randomMessageEntry()
        messageQueuePersistenceManager.add(entry).get()

        assertTrue(messageQueuePersistenceManager.remove(entry.metadata.userId, entry.metadata.messageId).get(), "Message not removed")
    }

    @Test
    fun `remove should do nothing if a message does not exist`() {
        assertFalse(messageQueuePersistenceManager.remove(randomUserId(), randomMessageId()).get(), "Message not removed")
    }

    @Test
    fun `removeAll should remove all the given message ids (user conversation)`() {
        val conversationId = randomUserConversationId()
        addContact(conversationId.id)
        val entries = (0..1).map {
            SenderMessageEntry(
                MessageMetadata(conversationId.id, null, MessageCategory.TEXT_SINGLE, randomMessageId()),
                randomSerializedMessage()
            )
        }

        messageQueuePersistenceManager.add(entries).get()

        val messageIds = entries.map { it.metadata.messageId }

        assertTrue(messageQueuePersistenceManager.removeAll(conversationId, messageIds).get(), "No changes reported")

        assertThat(messageQueuePersistenceManager.getUndelivered().get()).apply {
            `as`("Should remove all ids")
            isEmpty()
        }
    }

    @Test
    fun `removeAll should remove all the given message ids (group conversation)`() {
        withJoinedGroup { groupId, members ->
            val conversationId = groupId.toConversationId()

            val entries = members.map {
                SenderMessageEntry(
                    MessageMetadata(it, groupId, MessageCategory.TEXT_GROUP, randomMessageId()),
                    randomSerializedMessage()
                )
            }

            messageQueuePersistenceManager.add(entries).get()

            val messageIds = entries.map { it.metadata.messageId }

            assertTrue(messageQueuePersistenceManager.removeAll(conversationId, messageIds).get(), "No changes reported")

            assertThat(messageQueuePersistenceManager.getUndelivered().get()).apply {
                `as`("Should remove all ids")
                isEmpty()
            }
        }
    }

    @Test
    fun `removeAllForConversation should remove all messages for a conversation (user conversation)`() {
        val conversationId = randomUserConversationId()
        addContact(conversationId.id)
        val entries = (0..1).map {
            SenderMessageEntry(
                MessageMetadata(conversationId.id, null, MessageCategory.TEXT_SINGLE, randomMessageId()),
                randomSerializedMessage()
            )
        }

        messageQueuePersistenceManager.add(entries).get()

        assertTrue(messageQueuePersistenceManager.removeAllForConversation(conversationId).get(), "No changes reported")

        assertThat(messageQueuePersistenceManager.getUndelivered().get()).apply {
            `as`("Should remove all ids")
            isEmpty()
        }
    }

    @Test
    fun `removeAllForConversation should remove all messages for a conversation (group conversation)`() {
        withJoinedGroup { groupId, members ->
            val conversationId = groupId.toConversationId()

            val entries = members.map {
                SenderMessageEntry(
                    MessageMetadata(it, groupId, MessageCategory.TEXT_GROUP, randomMessageId()),
                    randomSerializedMessage()
                )
            }

            messageQueuePersistenceManager.add(entries).get()

            assertTrue(messageQueuePersistenceManager.removeAllForConversation(conversationId).get(), "No changes reported")

            assertThat(messageQueuePersistenceManager.getUndelivered().get()).apply {
                `as`("Should remove all ids")
                isEmpty()
            }
        }
    }

    @Test
    fun `getUndelivered should return all previously added messages in ascending order by id`() {
        val entries = randomMessageEntries(10)

        messageQueuePersistenceManager.add(entries).get()

        val undelivered = messageQueuePersistenceManager.getUndelivered().get()

        assertThat(undelivered.map { it.metadata }).apply {
            `as`("Returned values should be in order")
            containsExactlyElementsOf(entries.map { it.metadata })
        }
    }

    @Test
    fun `getUndelivered should return nothing if no messages were previously added`() {
        val undelivered = messageQueuePersistenceManager.getUndelivered().get()

        assertThat(undelivered)
            .`as`("Undelievered messages")
            .isEmpty()
    }
}