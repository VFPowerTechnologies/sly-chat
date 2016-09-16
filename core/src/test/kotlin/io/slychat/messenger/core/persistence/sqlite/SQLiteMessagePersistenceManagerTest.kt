package io.slychat.messenger.core.persistence.sqlite

import io.slychat.messenger.core.*
import io.slychat.messenger.core.crypto.randomMessageId
import io.slychat.messenger.core.persistence.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.*
import java.util.*
import kotlin.test.*

class SQLiteMessagePersistenceManagerTest : GroupPersistenceManagerTestUtils {
    companion object {
        @JvmStatic
        @BeforeClass
        fun loadLibrary() {
            SQLitePreKeyPersistenceManager::class.java.loadSQLiteLibraryFromResources()
        }
    }

    lateinit var persistenceManager: SQLitePersistenceManager
    lateinit override var contactsPersistenceManager: SQLiteContactsPersistenceManager
    lateinit override var groupPersistenceManager: SQLiteGroupPersistenceManager
    lateinit var messagePersistenceManager: SQLiteMessagePersistenceManager
    lateinit var conversationInfoTestUtils: ConversationInfoTestUtils

    @Before
    fun before() {
        persistenceManager = SQLitePersistenceManager(null, null, null)
        persistenceManager.init()
        contactsPersistenceManager = SQLiteContactsPersistenceManager(persistenceManager)
        groupPersistenceManager = SQLiteGroupPersistenceManager(persistenceManager)
        messagePersistenceManager = SQLiteMessagePersistenceManager(persistenceManager)
        conversationInfoTestUtils = ConversationInfoTestUtils(persistenceManager)
    }

    @After
    fun after() {
        persistenceManager.shutdown()
    }

    fun insertRandomReceivedMessagesFull(id: GroupId, members: Set<UserId>): List<ConversationMessageInfo> =
        insertRandomReceivedMessagesFull(id.toConversationId(), members)

    fun insertRandomReceivedMessagesFull(id: ConversationId, senders: Set<UserId>): List<ConversationMessageInfo> {
        return insertRandomReceivedMessagesFull(messagePersistenceManager, id, senders)
    }

    fun insertRandomReceivedMessagesFull(messagePersistenceManager: MessagePersistenceManager, id: ConversationId, senders: Set<UserId>): List<ConversationMessageInfo> {
        val info = ArrayList<ConversationMessageInfo>()

        senders.forEach { member ->
            (1..2).forEach {
                val groupMessageInfo = randomReceivedConversationMessageInfo(member)
                info.add(groupMessageInfo)

                messagePersistenceManager.addMessage(id, groupMessageInfo).get()
            }
        }

        return info
    }

    fun insertRandomSentMessage(id: GroupId): String =
        insertRandomSentMessage(id.toConversationId())

    fun insertRandomSentMessage(id: ConversationId): String {
        val conversationMessageInfo = randomSentConversationMessageInfo()
        messagePersistenceManager.addMessage(id, conversationMessageInfo).get()

        return conversationMessageInfo.info.id
    }

    fun insertRandomReceivedMessages(id: GroupId, members: Set<UserId>): List<String> =
        insertRandomReceivedMessages(id.toConversationId(), members)

    fun insertRandomReceivedMessages(id: ConversationId, senders: Set<UserId>): List<String> {
        return insertRandomReceivedMessagesFull(id, senders).map { it.info.id }
    }

    fun addRandomContact(allowedMessageLevel: AllowedMessageLevel = AllowedMessageLevel.ALL): UserId {
        val contactInfo = randomContactInfo(allowedMessageLevel)
        contactsPersistenceManager.add(contactInfo).get()
        return contactInfo.id
    }

    fun addMessage(userId: UserId, isSent: Boolean, message: String, ttl: Long): ConversationMessageInfo {
        val conversationMessageInfo = if (isSent)
            ConversationMessageInfo(null, randomSentMessageInfo().copy(message = message, ttlMs = ttl))
        else
            ConversationMessageInfo(userId, randomReceivedMessageInfo().copy(message = message, ttlMs = ttl))

        messagePersistenceManager.addMessage(ConversationId(userId), conversationMessageInfo).get()

        return conversationMessageInfo
    }

    fun getConversationInfo(conversationId: ConversationId): ConversationInfo {
        return assertNotNull(messagePersistenceManager.getConversationInfo(conversationId).get(), "No last conversation info")
    }

    //TODO we should clean up all these tests to use this instead, since due to the merge the majority of behavior is
    //identical between conversation types
    class MessageTestFixture() : GroupPersistenceManagerTestUtils {
        private val persistenceManager = SQLitePersistenceManager(null, null, null)

        val messagePersistenceManager = SQLiteMessagePersistenceManager(persistenceManager)
        override val contactsPersistenceManager = SQLiteContactsPersistenceManager(persistenceManager)
        override val groupPersistenceManager = SQLiteGroupPersistenceManager(persistenceManager)
        val conversationInfoTestUtils = ConversationInfoTestUtils(persistenceManager)

        fun addMessage(conversationId: ConversationId, speaker: UserId, isSent: Boolean, message: String, ttl: Long): ConversationMessageInfo {
            val conversationMessageInfo = if (isSent)
                ConversationMessageInfo(null, randomSentMessageInfo().copy(message = message, ttlMs = ttl))
            else
                ConversationMessageInfo(speaker, randomReceivedMessageInfo().copy(message = message, ttlMs = ttl))

            messagePersistenceManager.addMessage(conversationId, conversationMessageInfo).get()

            return conversationMessageInfo
        }

        fun addExpiringMessage(conversationId: ConversationId): ConversationMessageInfo {
            val sentMessage = randomSentConversationMessageInfo()
            val messageId = sentMessage.info.id
            val expiresAt = 10L

            messagePersistenceManager.addMessage(conversationId, sentMessage).get()
            messagePersistenceManager.setExpiration(conversationId, messageId, expiresAt).get()

            return ConversationMessageInfo(
                null,
                sentMessage.info.copy(expiresAt = expiresAt)
            )
        }

        fun getMessage(conversationId: ConversationId, messageId: String): ConversationMessageInfo {
            return assertNotNull(messagePersistenceManager.get(conversationId, messageId).get(), "Missing message $messageId")
        }

        fun run(body: MessageTestFixture.() -> Unit) {
            persistenceManager.init()

            try {
                this.body()
            }
            finally {
                persistenceManager.shutdown()
            }
        }
    }

    fun foreachConvType(body: MessageTestFixture.(ConversationId, Set<UserId>) -> Unit) {
        MessageTestFixture().run {
            val contactInfo = randomContactInfo(AllowedMessageLevel.ALL)
            val userId = contactInfo.id

            contactsPersistenceManager.add(contactInfo).get()

            this.body(userId.toConversationId(), setOf(userId))
        }

        MessageTestFixture().run {
            withJoinedGroup { groupId, members -> body(groupId.toConversationId(), members) }
        }
    }

    @Test
    fun `addMessage should add a valid sent message`() {
        foreachConvType { conversationId, participants ->
            val userId = participants.first()
            val ttl = 5L

            val messageText = randomMessageText()
            val inserted = addMessage(conversationId, userId, true, messageText, ttl)

            val got = getMessage(conversationId, inserted.info.id)
            val messageInfo = got.info

            assertEquals(messageText, messageInfo.message)
            assertEquals(ttl, messageInfo.ttlMs)
            assertTrue(messageInfo.isSent)
            assertFalse(messageInfo.isDelivered)
        }
    }

    @Test
    fun `addMessage should ignore messages with duplicate ids`() {
        val userId = addRandomContact()
        val conversationId = ConversationId(userId)

        val conversationMessageInfo = randomReceivedConversationMessageInfo(userId)

        messagePersistenceManager.addMessage(conversationId, conversationMessageInfo).get()
        messagePersistenceManager.addMessage(conversationId, conversationMessageInfo).get()
    }

    //not really sure there's a point to this
    @Test
    fun `addMessages should do nothing when given an empty list`() {
        val userId = ConversationId(randomUserId())

        messagePersistenceManager.addMessages(userId, emptyList()).get()
    }

    @Test
    fun `addMessage should add a valid received message`() {
        foreachConvType { conversationId, participants ->
            val userId = participants.first()

            val inserted = addMessage(conversationId, userId, false, randomMessageText(), 0)

            val got = getMessage(conversationId, inserted.info.id)

            assertEquals(inserted.info.message, got.info.message)
            assertFalse(got.info.isSent)
        }
    }

    @Test
    fun `addMessage should update conversation info when inserting a sent message`() {
        val conversationId = ConversationId(addRandomContact())

        val conversationMessageInfo = randomSentConversationMessageInfo()
        messagePersistenceManager.addMessage(conversationId, conversationMessageInfo).get()
        val lastConversationInfo = conversationInfoTestUtils.getConversationInfo(conversationId)

        assertEquals(conversationMessageInfo.info.timestamp, lastConversationInfo.lastTimestamp, "Timestamp wasn't updated")
        assertEquals(conversationMessageInfo.info.message, lastConversationInfo.lastMessage, "Message wasn't updated")
    }

    @Test
    fun `addMessage should throw InvalidMessageLevelException for a missing user table`() {
        val contactInfo = randomContactInfo(AllowedMessageLevel.GROUP_ONLY)
        contactsPersistenceManager.add(contactInfo).get()

        val userId = contactInfo.id
        val messageInfo = randomReceivedConversationMessageInfo(userId)

        assertFailsWith(InvalidMessageLevelException::class) {
            messagePersistenceManager.addMessage(ConversationId(userId), messageInfo).get()
        }
    }

    @Test
    fun `addMessage should log a message from another user`() {
        withJoinedGroup { groupId, members ->
            val conversationId = ConversationId(groupId)
            val sender = members.first()
            val groupMessageInfo = randomReceivedConversationMessageInfo(sender)

            messagePersistenceManager.addMessage(conversationId, groupMessageInfo).get()

            assertTrue(messagePersistenceManager.internalMessageExists(conversationId, groupMessageInfo.info.id), "Message not inserted")
        }
    }

    @Test
    fun `addMessage should log a message from yourself`() {
        withJoinedGroup { groupId, members ->
            val conversationId = ConversationId(groupId)
            val groupMessageInfo = randomReceivedConversationMessageInfo(null)

            messagePersistenceManager.addMessage(conversationId, groupMessageInfo).get()

            assertTrue(messagePersistenceManager.internalMessageExists(conversationId, groupMessageInfo.info.id), "Message not inserted")
        }
    }

    @Test
    fun `addMessage should update the corresponding group conversation info for a received message`() {
        withJoinedGroup { groupId, members ->
            val conversationId = ConversationId(groupId)
            val sender = members.first()
            val groupMessageInfo = randomReceivedConversationMessageInfo(sender)
            messagePersistenceManager.addMessage(conversationId, groupMessageInfo).get()

            val conversationInfo = conversationInfoTestUtils.getConversationInfo(conversationId)

            assertValidConversationInfo(groupMessageInfo, conversationInfo)
        }
    }

    @Test
    fun `addMessage should update the corresponding group conversation info for a self message`() {
        withJoinedGroup { groupId, members ->
            val conversationId = ConversationId(groupId)

            val groupMessageInfo = randomSentConversationMessageInfo()
            messagePersistenceManager.addMessage(conversationId, groupMessageInfo).get()

            val conversationInfo = conversationInfoTestUtils.getConversationInfo(conversationId)

            assertValidConversationInfo(groupMessageInfo, conversationInfo, 0)
        }
    }

    @Test
    fun `addMessage should obey insertion order when encountering duplicate timestamps`() {
        withJoinedGroup { groupId, members ->
            val conversationId = ConversationId(groupId)

            val speaker = members.first()
            val first = randomReceivedConversationMessageInfo(speaker)
            val second = ConversationMessageInfo(
                speaker,
                first.info.copy(id = randomMessageId())
            )

            messagePersistenceManager.addMessage(conversationId, first).get()
            messagePersistenceManager.addMessage(conversationId, second).get()

            val messages = messagePersistenceManager.internalGetAllMessages(conversationId)

            assertThat(messages).apply {
                `as`("Group messages")
                containsExactly(first, second)
            }
        }
    }

    @Test
    fun `addMessage should throw InvalidConversationException if the group id is invalid`() {
        assertFailsWithInvalidConversation {
          messagePersistenceManager.addMessage(randomGroupConversationId(), randomReceivedConversationMessageInfo(null)).get()
        }
    }

    fun testSingleUnreadInc(isRead: Boolean) {
        foreachConvType { conversationId, participants ->
            val speaker = participants.first()

            val messageInfo = randomReceivedMessageInfo(isRead)
            val conversationMessageInfo = ConversationMessageInfo(speaker, messageInfo)

            messagePersistenceManager.addMessage(conversationId, conversationMessageInfo).get()

            val conversationInfo = conversationInfoTestUtils.getConversationInfo(conversationId)

            if (!isRead)
                assertEquals(1, conversationInfo.unreadMessageCount, "Count should increase")
            else
                assertEquals(0, conversationInfo.unreadMessageCount, "Count should not increase")
        }
    }

    fun testMultiUnreadInc(isRead: Boolean) {
        foreachConvType { conversationId, participants ->
            val speaker = participants.first()

            val messageInfo = randomReceivedMessageInfo(isRead)
            val conversationMessageInfo = ConversationMessageInfo(speaker, messageInfo)

            //there to make sure the update doesn't rely on the last message's speaker
            val selfConversationMessageInfo = ConversationMessageInfo(null, randomSentMessageInfo())

            val messages = listOf(conversationMessageInfo, selfConversationMessageInfo)
            messagePersistenceManager.addMessages(conversationId, messages).get()

            val conversationInfo = conversationInfoTestUtils.getConversationInfo(conversationId)

            if (!isRead)
                assertEquals(1, conversationInfo.unreadMessageCount, "Count should increase")
            else
                assertEquals(0, conversationInfo.unreadMessageCount, "Count should not increase")
        }
    }

    @Test
    fun `addMessage should inc the conversation info unread count if an inserted message is unread`() {
        testSingleUnreadInc(false)
    }

    @Test
    fun `addMessage should not inc the conversation info unread count if an inserted message is read`() {
        testSingleUnreadInc(true)
    }

    @Test
    fun `addMessages should inc the conversation info unread count if an inserted message is unread`() {
        testMultiUnreadInc(false)
    }

    @Test
    fun `addMessages should not inc the conversation info unread count if an inserted message is read`() {
        testMultiUnreadInc(true)
    }

    @Test
    fun `addMessages should add all messages`() {
        val userId = addRandomContact()

        val base = currentTimestamp()
        val messages = listOf(
            ConversationMessageInfo(userId, MessageInfo.newReceived("message 1", base, 0)),
            ConversationMessageInfo(userId, MessageInfo.newReceived("message 2", base + 1000, 0))
        )

        val conversationId = ConversationId(userId)
        messagePersistenceManager.addMessages(conversationId, messages).get()
        val got = messagePersistenceManager.internalGetAllMessages(conversationId)

        assertThat(got).apply {
            `as`("Lists don't match")
            containsExactlyElementsOf(messages)
        }
    }

    @Test
    fun `getLastMessages should return the given message range in reverse order`() {
        val userId = addRandomContact()

        val messages = ArrayList<ConversationMessageInfo>()
        for (i in 0..9)
            messages.add(addMessage(userId, false, randomMessageText(), 0))

        val start = 4
        val count = 4
        val expected = messages.reversed().subList(start, start+count)

        val got = messagePersistenceManager.getLastMessages(ConversationId(userId), start, count).get()

        assertEquals(count, got.size)
        assertThat(got).apply {
            hasSize(count)
            containsExactlyElementsOf(expected)
        }
    }

    @Ignore
    @Test
    fun `getUndeliveredMessages should return all undelivered messages`() { TODO() }

    private fun assertEmptyLastConversationInfo(conversationInfo: ConversationInfo) {
        assertEquals(0, conversationInfo.unreadMessageCount, "Invalid unreadCount")
        assertNull(conversationInfo.lastMessage, "lastMessage isn't null")
        assertNull(conversationInfo.lastTimestamp, "lastTimestamp isn't null")
    }

    @Test
    fun `deleteAllMessages should remove all messages and update the conversion info for the corresponding user`() {
        val userId = addRandomContact()
        addMessage(userId, false, "received", 0)
        addMessage(userId, true, "sent", 0)

        val conversationId = ConversationId(userId)
        messagePersistenceManager.deleteAllMessages(conversationId).get()
        assertEquals(0, messagePersistenceManager.getLastMessages(conversationId, 0, 100).get().size, "Should not have any messages")

        val conversationInfo = getConversationInfo(conversationId)

        assertEmptyLastConversationInfo(conversationInfo)
    }

    @Test
    fun `deleteMessages should do nothing if the message list is empty`() {
        val userId = addRandomContact()

        addMessage(userId, false, "received", 0)
        addMessage(userId, true, "sent", 0)

        val conversationId = ConversationId(userId)
        messagePersistenceManager.deleteMessages(conversationId, emptyList()).get()

        assertEquals(2, messagePersistenceManager.getLastMessages(conversationId, 0, 100).get().size, "Message count doesn't match")
    }

    @Test
    fun `deleteMessages should remove the specified messages and update conversation info for the corresponding conversation (empty result)`() {
        val userId = addRandomContact()

        val conversationMessageInfo = addMessage(userId, false, "received", 0)

        val conversationId = ConversationId(userId)
        messagePersistenceManager.deleteMessages(conversationId, listOf(conversationMessageInfo.info.id)).get()

        val conversationInfo = getConversationInfo(conversationId)

        assertEmptyLastConversationInfo(conversationInfo)
    }

    @Test
    fun `deleteMessages should remove the specified messages and update conversation info for the corresponding conversation (remaining result)`() {
        val userId = addRandomContact()
        val conversationId = ConversationId(userId)

        val keep = ArrayList<ConversationMessageInfo>()
        val remove = ArrayList<ConversationMessageInfo>()

        for (i in 0..8) {
            val list = if (i % 2 == 0) remove else keep

            list.add(addMessage(userId, false, "received $i", 0))
            list.add(addMessage(userId, true, "sent $i", 0))
        }

        messagePersistenceManager.deleteMessages(conversationId, remove.map { it.info.id }).get()

        //match returned order
        val keepSorted = keep.reversed()
        val remainingSorted = messagePersistenceManager.getLastMessages(conversationId, 0, 100).get()

        assertEquals(keepSorted, remainingSorted, "Invalid remaining messages")

        val lastConversationInfo = getConversationInfo(conversationId)

        val lastMessage = keepSorted.first()

        //can't be done (see impl notes)
        //val expectedUnread = keepSorted.filter { it.isSent == false }.size

        //assertEquals(expectedUnread, lastConversationInfo.unreadCount, "Invalid unreadCount")
        assertEquals(lastMessage.info.message, lastConversationInfo.lastMessage, "lastMessage doesn't match")
        assertEquals(lastMessage.info.timestamp, lastConversationInfo.lastTimestamp, "lastTimestamp doesn't match")
    }

    fun assertValidConversationInfo(conversationMessageInfo: ConversationMessageInfo, conversationInfo: ConversationInfo, unreadCount: Int = 1) {
        assertEquals(conversationMessageInfo.speaker, conversationInfo.lastSpeaker, "Invalid speaker")
        assertEquals(conversationMessageInfo.info.message, conversationInfo.lastMessage, "Invalid last message")
        assertEquals(conversationMessageInfo.info.timestamp, conversationInfo.lastTimestamp, "Invalid last timestamp")
        assertEquals(unreadCount, conversationInfo.unreadMessageCount, "Invalid unread count")
    }

    fun assertFailsWithInvalidConversation(body: () -> Unit) {
        assertFailsWith(InvalidConversationException::class, body)
    }

    @Test
    fun `getGroupConversationInfo should return info for a joined group`() {
        withJoinedGroup { groupId, members ->
            assertNotNull(messagePersistenceManager.getConversationInfo(groupId.toConversationId()).get(), "No returned conversation info")
        }
    }

    @Test
    fun `getConversationInfo should return null for a nonexistent group`() {
         assertNull(messagePersistenceManager.getConversationInfo(randomGroupConversationId()).get())
    }

    @Test
    fun `getAllConversationInfo should return info only for joined groups`() {
        withJoinedGroup { joinedId, members ->
            withPartedGroup {
                withBlockedGroup {
                    val info = messagePersistenceManager.getAllGroupConversations().get()
                    assertThat(info.map { it.group.id }).apply {
                        `as`("Group conversation info")
                        containsOnly(joinedId)
                    }
                }
            }
        }
    }

    @Test
    fun `getAllGroupConversations should return nothing if no groups are available`() {
        assertTrue(messagePersistenceManager.getAllGroupConversations().get().isEmpty(), "Group list not empty")
    }

    @Test
    fun `deleteMessages should remove the given messages from the group log`() {
        withJoinedGroup { groupId, members ->
            val conversationId = groupId.toConversationId()
            val ids = insertRandomReceivedMessages(groupId, members)

            val toRemove = ids.subList(0, 2)
            val remaining = ids.subList(2, ids.size)

            messagePersistenceManager.deleteMessages(conversationId, toRemove).get()

            val messages = messagePersistenceManager.internalGetAllMessages(conversationId)

            assertThat(messages.map { it.info.id }).apply {
                `as`("Group messages")

                containsOnlyElementsOf(remaining)
            }
        }
    }

    @Test
    fun `deleteMessages should do nothing if the given messages are not present in the group log`() {
        withJoinedGroup { groupId, members ->
            val ids = insertRandomReceivedMessages(groupId, members)

            val conversationId = groupId.toConversationId()
            messagePersistenceManager.deleteMessages(conversationId, listOf(randomMessageId(), randomMessageId())).get()

            val messages = messagePersistenceManager.internalGetAllMessages(conversationId)

            assertThat(messages.map { it.info.id }).apply {
                `as`("Group messages")

                containsOnlyElementsOf(ids)
            }
        }
    }

    @Test
    fun `deleteMessages should update the corresponding group conversation info when some messages remain`() {
        withJoinedGroup { groupId, members ->
            val info = insertRandomReceivedMessagesFull(groupId, members)
            val ids = info.map { it.info.id }

            val toRemove = ids.subList(0, 2)

            val conversationId = groupId.toConversationId()
            messagePersistenceManager.deleteMessages(conversationId, toRemove).get()

            //should contain the last inserted message
            val convoInfo = conversationInfoTestUtils.getConversationInfo(conversationId)

            val lastMessageInfo = info.last()

            assertEquals(lastMessageInfo.speaker, convoInfo.lastSpeaker, "Invalid last speaker")
            assertEquals(lastMessageInfo.info.timestamp, convoInfo.lastTimestamp, "Invalid last time timestamp")
            assertEquals(lastMessageInfo.info.message, convoInfo.lastMessage, "Invalid last message")
        }
    }

    @Test
    fun `deleteMessages should update the corresponding group conversation info when no messages remain`() {
        withJoinedGroup { groupId, members ->
            val ids = insertRandomReceivedMessages(groupId, members)

            messagePersistenceManager.deleteMessages(groupId.toConversationId(), ids).get()

            conversationInfoTestUtils.assertInitialConversationInfo(groupId)
        }
    }

    @Test
    fun `deleteMessages should remove expiring message entries`() {
        foreachConvType { conversationId, set ->
            val sentMessage = randomSentConversationMessageInfo()
            val messageId = sentMessage.info.id
            val expiresAt = 10L

            messagePersistenceManager.addMessage(conversationId, sentMessage).get()

            messagePersistenceManager.setExpiration(conversationId, messageId, expiresAt).get()

            messagePersistenceManager.deleteMessages(conversationId, listOf(messageId))

            val awaitingExpiration = messagePersistenceManager.getMessagesAwaitingExpiration().get()
            assertThat(awaitingExpiration).apply {
                `as`("Expiring messages should be deleted")
                isEmpty()
            }
        }
    }

    @Test
    fun `deleteMessages should throw InvalidConversationException if the group id is invalid`() {
        assertFailsWithInvalidConversation {
            //XXX this won't actually fail for an empty list
            messagePersistenceManager.deleteMessages(randomGroupConversationId(), listOf(randomMessageId())).get()
        }
    }

    @Test
    fun `deleteAllMessages should clear the entire group log`() {
        withJoinedGroup { groupId, members ->
            insertRandomReceivedMessages(groupId, members)

            val conversationId = groupId.toConversationId()
            messagePersistenceManager.deleteAllMessages(conversationId).get()

            val messages = messagePersistenceManager.internalGetAllMessages(conversationId)

            assertThat(messages).apply {
                `as`("Group messages")
                isEmpty()
            }
        }
    }

    @Test
    fun `deleteAllMessages should remove expiring message entries`() {
        foreachConvType { conversationId, set ->
            val sentMessage = randomSentConversationMessageInfo()
            val messageId = sentMessage.info.id
            val expiresAt = 10L

            messagePersistenceManager.addMessage(conversationId, sentMessage).get()

            messagePersistenceManager.setExpiration(conversationId, messageId, expiresAt).get()

            messagePersistenceManager.deleteAllMessages(conversationId)

            val awaitingExpiration = messagePersistenceManager.getMessagesAwaitingExpiration().get()
            assertThat(awaitingExpiration).apply {
                `as`("Expiring messages should be deleted")
                isEmpty()
            }
        }
    }

    @Test
    fun `deleteAllMessages should update the corresponding group conversation info`() {
        withJoinedGroup { groupId, members ->
            insertRandomReceivedMessages(groupId, members)

            messagePersistenceManager.deleteAllMessages(groupId.toConversationId()).get()

            conversationInfoTestUtils.assertInitialConversationInfo(groupId)
        }
    }

    @Test
    fun `deleteAllMessages should throw InvalidConversationException if the group id is invalid`() {
        withJoinedGroup { groupId, members ->
            insertRandomReceivedMessages(groupId, members)

            messagePersistenceManager.deleteAllMessages(groupId.toConversationId()).get()

            conversationInfoTestUtils.assertInitialConversationInfo(groupId)
        }
    }

    @Test
    fun `markMessageAsDelivered should update isDelivered and receivedTimestamp fields`() {
        val userId = addRandomContact()

        val conversationMessageInfo = addMessage(userId, true, randomMessageText(), 0)

        val receivedTimestamp = currentTimestamp() - 1000
        val updatedMessageInfo = assertNotNull(messagePersistenceManager.markMessageAsDelivered(userId.toConversationId(), conversationMessageInfo.info.id, receivedTimestamp).get(), "Message not updated")

        assertEquals(receivedTimestamp, updatedMessageInfo.info.receivedTimestamp)
        assertTrue(updatedMessageInfo.info.isDelivered)
    }

    @Test
    fun `markMessageAsDelivered should set the given message delivery status to delivered if the message exists`() {
        withJoinedGroup { groupId, members ->
            val id = insertRandomSentMessage(groupId)

            val receivedTimestamp = currentTimestamp() - 1000

            val groupMessageInfo = assertNotNull(messagePersistenceManager.markMessageAsDelivered(groupId.toConversationId(), id, receivedTimestamp).get(), "Info wasn't returned")

            assertTrue(groupMessageInfo.info.isDelivered, "Not marked as delivered")
            assertEquals(receivedTimestamp, groupMessageInfo.info.receivedTimestamp, "Received timestamp set to unexpected value")
        }
    }

    @Test
    fun `markMessageAsDelievered should return null if the message has already been marked as delievered`() {
        withJoinedGroup { groupId, members ->
            val id = insertRandomSentMessage(groupId)

            val conversationId = groupId.toConversationId()
            assertNotNull(messagePersistenceManager.markMessageAsDelivered(conversationId, id, currentTimestamp()).get(), "Info wasn't returned")
            assertNull(messagePersistenceManager.markMessageAsDelivered(conversationId, id, currentTimestamp()).get(), "Message not marked as delievered")
        }
    }

    @Test
    fun `markMessageAsDelivered should throw InvalidGroupMessageException if the given message if the message does not exist`() {
        withJoinedGroup { groupId, members ->
            assertFailsWith(InvalidConversationMessageException::class) {
                messagePersistenceManager.markMessageAsDelivered(groupId.toConversationId(), randomMessageId(), currentTimestamp()).get()
            }
        }
    }

    @Test
    fun `markMessageAsDelivered should throw InvalidConversationException if the group id is invalid`() {
        assertFailsWithInvalidConversation { messagePersistenceManager.markMessageAsDelivered(randomGroupConversationId(), randomMessageId(), currentTimestamp()).get() }
    }

    @Test
    fun `markConversationAsRead should reset the unread count`() {
        withJoinedGroup { groupId, members ->
            val conversationInfo = ConversationInfo(
                members.first(),
                1,
                randomMessageText(),
                currentTimestamp()
            )
            val conversationId = groupId.toConversationId()

            conversationInfoTestUtils.setConversationInfo(conversationId, conversationInfo)

            messagePersistenceManager.markConversationAsRead(conversationId).get()

            val got = conversationInfoTestUtils.getConversationInfo(groupId)

            assertEquals(0, got.unreadMessageCount, "Unread count not reset")
        }
    }

    @Test
    fun `markConversationAsRead should throw InvalidConversationException for a non-existent group`() {
        assertFailsWithInvalidConversation { messagePersistenceManager.markConversationAsRead(randomGroupConversationId()).get() }
    }

    @Test
    fun `markConversationAsRead should mark all isRead messages as true`() {
        foreachConvType { conversationId, participants ->
            val messages = insertRandomReceivedMessagesFull(messagePersistenceManager, conversationId, participants)

            messagePersistenceManager.markConversationAsRead(conversationId).get()

            messages.forEach {
                val messageId = it.info.id
                val conversationMessageInfo = getMessage(conversationId, messageId)
                assertTrue(conversationMessageInfo.info.isRead, "Message $messageId not marked as read")
            }
        }
    }

    @Test
    fun `getLastMessages should return the asked for message range`() {
        withJoinedGroup { groupId, members ->
            val ids = insertRandomReceivedMessages(groupId, members)

            val lastMessageIds = messagePersistenceManager.getLastMessages(groupId.toConversationId(), 0, 2).get().map { it.info.id }
            val expectedIds = ids.subList(ids.size-2, ids.size).reversed()

            assertThat(lastMessageIds).apply {
                `as`("Last messages")
                containsExactlyElementsOf(expectedIds)
            }
        }
    }

    @Test
    fun `getLastMessages should return nothing if the range does not exist`() {
        withJoinedGroup { groupId, members ->
            val lastMessages = messagePersistenceManager.getLastMessages(groupId.toConversationId(), 0, 100).get()

            assertThat(lastMessages).apply {
                `as`("Last messages")
                isEmpty()
            }
        }
    }

    @Test
    fun `getLastMessages should throw InvalidConversationException if the group id is invalid`() {
        assertFailsWithInvalidConversation { messagePersistenceManager.getLastMessages(randomGroupConversationId(), 0, 100).get() }
    }

    @Test
    fun `getAllGroupConversations should return an empty list if no conversations are available`() {
        assertTrue(messagePersistenceManager.getAllGroupConversations().get().isEmpty())
    }

    @Test
    fun `getAllUserConversations should return conversations for ALL users`() {
        addRandomContact()
        addRandomContact()
        addRandomContact()

        val got = messagePersistenceManager.getAllUserConversations().get()

        assertEquals(3, got.size)
    }

    fun testNoUserConversations() {
        val got = messagePersistenceManager.getAllUserConversations().get()
        assertThat(got).apply {
            `as`("There should be no conversations")
            isEmpty()
        }
    }

    @Test
    fun `getAllUserConversations should ignore GROUP_ONLY users`() {
        addRandomContact(AllowedMessageLevel.GROUP_ONLY)

        testNoUserConversations()
    }

    @Test
    fun `getAllUserConversations should ignore BLOCKED users`() {
        addRandomContact(AllowedMessageLevel.BLOCKED)

        testNoUserConversations()
    }

    @Test
    fun `getAllUserConversations should return a last message field if messages are available`() {
        val userId = addRandomContact()
        val userId2 = addRandomContact()

        val lastMessage = randomMessageText()

        conversationInfoTestUtils.setConversationInfo(
            ConversationId(userId),
            ConversationInfo(null, 2, lastMessage, currentTimestamp())
        )

        val convos = messagePersistenceManager.getAllUserConversations().get()

        val a = convos.find { it.contact.id == userId }!!
        val a2 = convos.find { it.contact.id == userId2 }!!

        assertEquals(lastMessage, a.info.lastMessage)
        assertNull(a2.info.lastMessage)
    }

    @Test
    fun `getConversationInfo should return a conversation for an ALL contact`() {
        val id = addRandomContact(AllowedMessageLevel.ALL)

        assertNotNull(messagePersistenceManager.getConversationInfo(ConversationId(id)).get(), "Missing conversation info")
    }

    @Test
    fun `getConversationInfo should return nothing for a GROUP_ONLY contact`() {
        val id = addRandomContact(AllowedMessageLevel.GROUP_ONLY)

        assertNull(messagePersistenceManager.getConversationInfo(ConversationId(id)).get(), "Conversation info should not exist")
    }

    @Test
    fun `getConversationInfo should return nothing for a BLOCKED contact`() {
        val id = addRandomContact(AllowedMessageLevel.BLOCKED)

        assertNull(messagePersistenceManager.getConversationInfo(ConversationId(id)).get(), "Conversation info should not exist")
    }

    @Test
    fun `getConversation should return null if the given conversation doesn't exist`() {
        assertNull(messagePersistenceManager.getConversationInfo(randomUserConversationId()).get())
    }

    @Test
    fun `markConversationAsRead should mark all unread messages as read`() {
        val userId = addRandomContact()
        val conversationId = ConversationId(userId)

        conversationInfoTestUtils.setConversationInfo(conversationId, ConversationInfo(null, 2, randomMessageText(), currentTimestamp()))

        messagePersistenceManager.markConversationAsRead(conversationId).get()

        val got = assertNotNull(messagePersistenceManager.getConversationInfo(conversationId).get(), "Missing conversation info")
        assertEquals(0, got.unreadMessageCount, "Unread count should be 0")
    }

    @Test
    fun `markConversationAsRead should throw InvalidConversationException if the given conversation doesn't exist`() {
        assertFailsWith(InvalidConversationException::class) {
            messagePersistenceManager.markConversationAsRead(randomUserConversationId()).get()
        }
    }

    @Test
    fun `setExpiration should throw InvalidConversationMessageException if the message does not exists`() {
        foreachConvType { conversationId, participants ->
            assertFailsWith(InvalidConversationMessageException::class) {
                messagePersistenceManager.setExpiration(conversationId, randomMessageId(), 1).get()
            }
        }
    }

    @Test
    fun `setExpiration should create an expiring_messages entry for the given message`() {
        foreachConvType { conversationId, participants ->
            val conversationMessageInfo = addExpiringMessage(conversationId)
            val messageId = conversationMessageInfo.info.id
            val expiresAt = conversationMessageInfo.info.expiresAt

            val awaitingExpiration = messagePersistenceManager.getMessagesAwaitingExpiration().get()

            val expected = ExpiringMessage(conversationId, messageId, expiresAt)

            assertThat(awaitingExpiration).apply {
                `as`("Should contain an expiring entry")
                containsOnly(expected)
            }
        }
    }

    @Test
    fun `setExpiration should do nothing for an already expiring message`() {
        foreachConvType { conversationId, participants ->
            val conversationMessageInfo = addExpiringMessage(conversationId)
            val messageId = conversationMessageInfo.info.id
            val expiresAt = conversationMessageInfo.info.expiresAt

            assertFalse(messagePersistenceManager.setExpiration(conversationId, messageId, expiresAt + 1).get())

            val got = getMessage(conversationId, messageId)

            assertEquals(expiresAt, got.info.expiresAt, "Record should not be updated")
        }
    }

    //this is kinda lacking
    @Test
    fun `expireMessages should set all message entries to expired`() {
        foreachConvType { conversationId, participants ->
            val conversationMessageInfo = addExpiringMessage(conversationId)
            val messageId = conversationMessageInfo.info.id

            val messages = mapOf(
                conversationId to listOf(messageId)
            )

            messagePersistenceManager.expireMessages(messages).get()

            val got = getMessage(conversationId, messageId)

            assertTrue(got.info.isExpired, "Message should be marked as expired")
            assertEquals(0, got.info.ttlMs, "TTL not reset")
            assertEquals(0, got.info.expiresAt, "expiresAt not reset")
            assertEquals("", got.info.message, "Message text not deleted")
        }
    }

    @Test
    fun `expireMessages should remove all message entries from expiring list`() {
        foreachConvType { conversationId, participants ->
            val conversationMessageInfo = addExpiringMessage(conversationId)
            val messageId = conversationMessageInfo.info.id

            val messages = mapOf(
                conversationId to listOf(messageId)
            )

            messagePersistenceManager.expireMessages(messages).get()

            assertThat(messagePersistenceManager.getMessagesAwaitingExpiration().get()).apply {
                `as`("Should remove related expiring entries")
                isEmpty()
            }
        }
    }

    @Test
    fun `getConversationDisplayInfo should return data when last message data is available`() {
        foreachConvType { conversationId, participants ->
            val messageText = randomMessageText()
            val speaker = participants.first()

            val conversationMessageInfo = addMessage(conversationId, speaker, false, messageText, 0)

            val groupName = when (conversationId) {
                is ConversationId.Group -> groupPersistenceManager.getInfo(conversationId.id).get()!!.name
                else -> null
            }

            val speakerName = contactsPersistenceManager.get(speaker).get()!!.name

            val lastMessageData = LastMessageData(
                speakerName,
                conversationMessageInfo.info.message,
                conversationMessageInfo.info.timestamp
            )

            val expected = ConversationDisplayInfo(conversationId, groupName, 1, lastMessageData)

            val conversationDisplayInfo = messagePersistenceManager.getConversationDisplayInfo(conversationId).get()

            assertEquals(expected, conversationDisplayInfo, "Invalid display info")
        }
    }
}