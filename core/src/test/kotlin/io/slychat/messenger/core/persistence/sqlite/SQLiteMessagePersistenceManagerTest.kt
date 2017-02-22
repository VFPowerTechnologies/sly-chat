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

    private lateinit var persistenceManager: SQLitePersistenceManager
    lateinit override var contactsPersistenceManager: SQLiteContactsPersistenceManager
    lateinit override var groupPersistenceManager: SQLiteGroupPersistenceManager
    private lateinit var messagePersistenceManager: SQLiteMessagePersistenceManager
    private lateinit var conversationInfoTestUtils: ConversationInfoTestUtils

    @Before
    fun before() {
        persistenceManager = SQLitePersistenceManager(null, null)
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

    private fun insertRandomReceivedMessagesFull(id: GroupId, members: Set<UserId>): List<ConversationMessageInfo> =
        insertRandomReceivedMessagesFull(id.toConversationId(), members)

    private fun insertRandomReceivedMessagesFull(id: ConversationId, senders: Set<UserId>): List<ConversationMessageInfo> {
        return insertRandomReceivedMessagesFull(messagePersistenceManager, id, senders)
    }

    private fun insertRandomReceivedMessagesFull(messagePersistenceManager: MessagePersistenceManager, id: ConversationId, senders: Set<UserId>): List<ConversationMessageInfo> {
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

    private fun insertRandomSentMessage(id: GroupId): String =
        insertRandomSentMessage(id.toConversationId())

    private fun insertRandomSentMessage(id: ConversationId): String {
        val conversationMessageInfo = randomSentConversationMessageInfo()
        messagePersistenceManager.addMessage(id, conversationMessageInfo).get()

        return conversationMessageInfo.info.id
    }

    private fun insertRandomReceivedMessages(id: GroupId, members: Set<UserId>): List<String> =
        insertRandomReceivedMessages(id.toConversationId(), members)

    private fun insertRandomReceivedMessages(id: ConversationId, senders: Set<UserId>): List<String> {
        return insertRandomReceivedMessagesFull(id, senders).map { it.info.id }
    }

    private fun addRandomContact(allowedMessageLevel: AllowedMessageLevel = AllowedMessageLevel.ALL): UserId {
        val contactInfo = randomContactInfo(allowedMessageLevel)
        contactsPersistenceManager.add(contactInfo).get()
        return contactInfo.id
    }

    private fun addMessage(userId: UserId, isSent: Boolean, message: String, ttl: Long): ConversationMessageInfo {
        val conversationMessageInfo = if (isSent)
            ConversationMessageInfo(null, randomSentMessageInfo().copy(message = message, ttlMs = ttl))
        else
            ConversationMessageInfo(userId, randomReceivedMessageInfo().copy(message = message, ttlMs = ttl))

        messagePersistenceManager.addMessage(ConversationId(userId), conversationMessageInfo).get()

        return conversationMessageInfo
    }

    private fun getConversationInfo(conversationId: ConversationId): ConversationInfo {
        return assertNotNull(messagePersistenceManager.getConversationInfo(conversationId).get(), "No last conversation info")
    }

    //TODO we should clean up all these tests to use this instead, since due to the merge the majority of behavior is
    //identical between conversation types
    private class MessageTestFixture() : GroupPersistenceManagerTestUtils {
        private val persistenceManager = SQLitePersistenceManager(null, null)

        val messagePersistenceManager = SQLiteMessagePersistenceManager(persistenceManager)
        override val contactsPersistenceManager = SQLiteContactsPersistenceManager(persistenceManager)
        override val groupPersistenceManager = SQLiteGroupPersistenceManager(persistenceManager)
        val conversationInfoTestUtils = ConversationInfoTestUtils(persistenceManager)

        fun getConversationInfo(conversationId: ConversationId): ConversationInfo {
            return assertNotNull(conversationInfoTestUtils.getConversationInfo(conversationId), "No last conversation info")
        }

        fun getConversationNameForConversation(conversationId: ConversationId): String {
            return when (conversationId) {
                is ConversationId.Group -> groupPersistenceManager.getInfo(conversationId.id).get()!!.name
                is ConversationId.User -> contactsPersistenceManager.get(conversationId.id).get()!!.name
            }
        }

        fun addMessage(conversationId: ConversationId, speaker: UserId?, messageInfo: MessageInfo, failures: Map<UserId, MessageSendFailure> = emptyMap()): ConversationMessageInfo {
            val conversationMessageInfo = ConversationMessageInfo(speaker, messageInfo, failures)
            return addMessage(conversationId, conversationMessageInfo)
        }

        fun addMessage(conversationId: ConversationId, conversationMessageInfo: ConversationMessageInfo): ConversationMessageInfo {
            messagePersistenceManager.addMessage(conversationId, conversationMessageInfo).get()

            return conversationMessageInfo
        }

        fun addMessage(conversationId: ConversationId, speaker: UserId?, isSent: Boolean, message: String, ttl: Long): ConversationMessageInfo {
            val messageInfo = if (isSent)
                randomSentMessageInfo().copy(message = message, ttlMs = ttl)
            else
                randomReceivedMessageInfo().copy(message = message, ttlMs = ttl)

            return addMessage(conversationId, speaker, messageInfo)
        }

        fun generateExpiringMessages(conversationId: ConversationId): Map<ConversationId, List<String>> {
            val info = (0..1).map {
                addExpiringSentMessage(conversationId)
            }

            val messageIds = info.map { it.info.id }

            return mapOf(
                conversationId to messageIds
            )
        }

        fun addExpiringSentMessage(conversationId: ConversationId): ConversationMessageInfo {
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

        fun addExpiringReceivedMessage(conversationId: ConversationId, sender: UserId): ConversationMessageInfo {
            val sentMessage = randomReceivedConversationMessageInfo(sender)
            val messageId = sentMessage.info.id
            val expiresAt = 10L

            messagePersistenceManager.addMessage(conversationId, sentMessage).get()
            messagePersistenceManager.setExpiration(conversationId, messageId, expiresAt).get()

            return ConversationMessageInfo(
                sender,
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

    private fun forUserConvType(allowedMessageLevel: AllowedMessageLevel, body: MessageTestFixture.(ContactInfo) -> Unit) {
        MessageTestFixture().run {
            val contactInfo = randomContactInfo(allowedMessageLevel)

            contactsPersistenceManager.add(contactInfo).get()

            this.body(contactInfo)
        }
    }

    private fun foreachConvType(body: MessageTestFixture.(ConversationId, Set<UserId>) -> Unit) {
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
            val ttl = 5L

            val messageText = randomMessageText()
            val inserted = addMessage(conversationId, null, true, messageText, ttl)

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
            val groupMessageInfo = randomSentConversationMessageInfo()

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

    private fun testSingleUnreadInc(isRead: Boolean) {
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

    private fun testMultiUnreadInc(isRead: Boolean) {
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
    fun `addMessage should insert failures if present`() {
        foreachConvType { conversationId, participants ->
            val speaker = participants.first()
            val failures = mapOf(
                speaker to MessageSendFailure.InactiveUser()
            )

            val conversationMessageInfo = ConversationMessageInfo(speaker, randomReceivedMessageInfo(), failures)
            messagePersistenceManager.addMessage(conversationId, conversationMessageInfo).get()

            val messageId = conversationMessageInfo.info.id

            val updatedInfo = assertNotNull(messagePersistenceManager.get(conversationId, messageId).get(), "Missing message")

            assertThat(updatedInfo.failures).apply {
                `as`("Updated value should contain the updated failures")
                containsAllEntriesOf(failures)
            }
        }
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
    fun `deleteAllMessages should return the last message timestamp if the conversation log is non-empty`() {
        foreachConvType { conversationId, participants ->
            addMessage(conversationId, participants.first(), false, randomMessageText(), 0)

            val conversationMessageInfo = addMessage(conversationId, participants.first(), false, randomMessageText(), 0)

            val lastMessageTimestamp = messagePersistenceManager.deleteAllMessages(conversationId).get()

            assertEquals(conversationMessageInfo.info.timestamp, lastMessageTimestamp, "Invalid message id returned")
        }
    }

    //here as a precaution because I actually made this mistake
    @Test
    fun `deleteAllMessages should return the last message timestamp if the last message expired`() {
        foreachConvType { conversationId, participants ->
            val conversationMessageInfo = addMessage(conversationId, participants.first(), false, randomMessageText(), 0)
            val messageId = conversationMessageInfo.info.id

            messagePersistenceManager.expireMessages(mapOf(conversationId to listOf(messageId))).get()

            val lastMessageTimestamp = messagePersistenceManager.deleteAllMessages(conversationId).get()

            assertEquals(conversationMessageInfo.info.timestamp, lastMessageTimestamp, "Invalid message id returned")
        }
    }

    @Test
    fun `deleteAllMessages should return null if the conversation log is empty`() {
        foreachConvType { conversationId, participants ->
            val messageId = messagePersistenceManager.deleteAllMessages(conversationId).get()

            assertNull(messageId, "Should return null if no messages are present")
        }
    }

    @Test
    fun `deleteAllMessagesUntil should remove messages up to and including the given timestamp`() {
        foreachConvType { conversationId, participants ->
            var t = 0L

            val speaker = participants.first()

            (0..3).forEach {
                val timestamp = t
                ++t

                val messageInfo = MessageInfo.newReceived(randomMessageText(), timestamp, 0)

                addMessage(conversationId, speaker, messageInfo)
            }

            val deleteUntilTimestamp = t
            ++t
            addMessage(conversationId, speaker, MessageInfo.newReceived(randomMessageText(), deleteUntilTimestamp, 0))

            val messageInfo = MessageInfo.newReceived(randomMessageText(), t, 0)
            val remainingMessageId = messageInfo.id
            addMessage(conversationId, speaker, messageInfo)

            messagePersistenceManager.deleteAllMessagesUntil(conversationId, deleteUntilTimestamp).get()

            val remainingIds = messagePersistenceManager.getLastMessages(conversationId, 0, 100).get().map { it.info.id }

            assertThat(remainingIds).apply {
                `as`("Should only contain message after the deletion point")
                containsOnly(remainingMessageId)
            }
        }
    }

    @Test
    fun `deleteAllMessagesUntil should update the conversation info`() {
        foreachConvType { conversationId, participants ->
            val conversationMessageInfo = addMessage(conversationId, participants.first(), false, randomMessageText(), 0)

            messagePersistenceManager.deleteAllMessagesUntil(conversationId, conversationMessageInfo.info.timestamp).get()

            val conversationInfo = getConversationInfo(conversationId)
            assertEquals(ConversationInfo(null, 0, null, null), conversationInfo, "Conversation info not updated")
        }
    }

    @Test
    fun `deleteAllMessagesUntil should remove any deleted expiring entries`() {
        foreachConvType { conversationId, participants ->
            val messages = (0..1L).map {
               MessageInfo.newReceived(randomMessageText(), it, 10)
            }

            messages.forEach {
                addMessage(conversationId, participants.first(), it)

                messagePersistenceManager.setExpiration(conversationId, it.id, 200).get()
            }

            messagePersistenceManager.deleteAllMessagesUntil(conversationId, messages.first().timestamp).get()

            assertThat(messagePersistenceManager.getMessagesAwaitingExpiration().get().map { it.messageId }).apply {
                `as`("Should remove expiring messages entries")
                containsOnly(messages[1].id)
            }
        }
    }

    @Test
    fun `deleteAllMessagesUntil should remove any associated message failures`() {
        foreachConvType { conversationId, participants ->
            val speaker = participants.first()

            val conversationMessageInfo = addMessage(conversationId, speaker, randomReceivedMessageInfo(), randomMessageSendFailures(speaker))

            val messageId = conversationMessageInfo.info.id

            messagePersistenceManager.deleteAllMessagesUntil(conversationId, conversationMessageInfo.info.timestamp).get()

            assertThat(messagePersistenceManager.internalGetFailures(conversationId, messageId)).apply {
                `as`("deleteAllMessagesUntil should remove failures")
                isEmpty()
            }
        }
    }

    @Test
    fun `deleteMessages should remove any associated message failures`() {
        foreachConvType { conversationId, participants ->
            val speaker = participants.first()

            val conversationMessageInfo = addMessage(conversationId, speaker, randomReceivedMessageInfo(), randomMessageSendFailures(speaker))

            val messageId = conversationMessageInfo.info.id

            messagePersistenceManager.deleteMessages(conversationId, listOf(messageId)).get()

            assertThat(messagePersistenceManager.internalGetFailures(conversationId, messageId)).apply {
                `as`("deleteMessages should remove failures")
                isEmpty()
            }
        }
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

    private fun assertValidConversationInfo(conversationMessageInfo: ConversationMessageInfo, conversationInfo: ConversationInfo, unreadCount: Int = 1) {
        assertEquals(conversationMessageInfo.speaker, conversationInfo.lastSpeaker, "Invalid speaker")
        assertEquals(conversationMessageInfo.info.message, conversationInfo.lastMessage, "Invalid last message")
        assertEquals(conversationMessageInfo.info.timestamp, conversationInfo.lastTimestamp, "Invalid last timestamp")
        assertEquals(unreadCount, conversationInfo.unreadMessageCount, "Invalid unread count")
    }

    private fun assertFailsWithInvalidConversation(body: () -> Unit) {
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
    fun `deleteAllMessages should remove any associated message failures`() {
        foreachConvType { conversationId, participants ->
            val speaker = participants.first()

            val conversationMessageInfo = addMessage(conversationId, speaker, randomReceivedMessageInfo(), randomMessageSendFailures(speaker))

            val messageId = conversationMessageInfo.info.id

            messagePersistenceManager.deleteAllMessages(conversationId).get()

            assertThat(messagePersistenceManager.internalGetFailures(conversationId, messageId)).apply {
                `as`("deleteAllMessages should remove failures")
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

    private fun testNoUserConversations() {
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
    fun `markConversationAsRead should reset the unread count`() {
        foreachConvType { conversationId, participants ->
            conversationInfoTestUtils.setConversationInfo(conversationId, ConversationInfo(null, 2, randomMessageText(), currentTimestamp()))

            messagePersistenceManager.markConversationAsRead(conversationId).get()

            val got = assertNotNull(messagePersistenceManager.getConversationInfo(conversationId).get(), "Missing conversation info")
            assertEquals(0, got.unreadMessageCount, "Unread count should be 0")
        }
    }

    @Test
    fun `markConversationAsRead should return message ids for messages marked as read`() {
        foreachConvType { conversationId, participants ->
            val speaker = participants.first()
            val messageIds = (0..1).map { addMessage(conversationId, speaker, false, randomMessageText(), 0).info.id }

            val got = messagePersistenceManager.markConversationAsRead(conversationId).get()

            assertThat(got).apply {
                `as`("Should return read message ids")
                containsOnlyElementsOf(messageIds)
            }
        }
    }

    @Test
    fun `markConversationAsRead should throw InvalidConversationException for a non-existent group`() {
        listOf(randomUserConversationId(), randomGroupConversationId()).forEach {
            assertFailsWithInvalidConversation { messagePersistenceManager.markConversationAsRead(it).get() }
        }
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
            val conversationMessageInfo = addExpiringSentMessage(conversationId)
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
            val conversationMessageInfo = addExpiringSentMessage(conversationId)
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
            val messages = generateExpiringMessages(conversationId)
            val messageIds = messages[conversationId]!!

            messagePersistenceManager.expireMessages(messages).get()

            messageIds.forEach { messageId ->
                val got = getMessage(conversationId, messageId)

                assertTrue(got.info.isExpired, "Message should be marked as expired")
                assertEquals(0, got.info.ttlMs, "TTL not reset")
                assertEquals(0, got.info.expiresAt, "expiresAt not reset")
                assertEquals("", got.info.message, "Message text not deleted")
            }
        }
    }

    @Test
    fun `expireMessages should remove all message entries from expiring list`() {
        foreachConvType { conversationId, participants ->
            val messages = generateExpiringMessages(conversationId)

            messagePersistenceManager.expireMessages(messages).get()

            assertThat(messagePersistenceManager.getMessagesAwaitingExpiration().get()).apply {
                `as`("Should remove related expiring entries")
                isEmpty()
            }
        }
    }

    //technically this should be handled by message read sync, but for consistency
    @Test
    fun `expireMessages should mark messages as read`() {
        foreachConvType { conversationId, participants ->
            val messages = generateExpiringMessages(conversationId)
            val messageIds = messages[conversationId]!!

            messagePersistenceManager.expireMessages(messages).get()

            messageIds.forEach{ messageId ->
                val messageInfo = getMessage(conversationId, messageId)

                assertTrue(messageInfo.info.isRead, "Message not marked as read")
            }
        }
    }

    @Test
    fun `expireMessages should update the conversation info`() {
        foreachConvType { conversationId, participants ->
            val conversationName = getConversationNameForConversation(conversationId)

            val conversationMessageInfo = addExpiringReceivedMessage(conversationId, participants.first())
            val messageId = conversationMessageInfo.info.id

            val messages = mapOf(
                conversationId to listOf(messageId)
            )

            messagePersistenceManager.expireMessages(messages).get()

            val conversationDisplayInfo = messagePersistenceManager.getConversationDisplayInfo(conversationId).get()

            assertEquals(ConversationDisplayInfo(
                conversationId,
                conversationName,
                0,
                emptyList(),
                null
            ), conversationDisplayInfo, "Conversation info not updated")
        }
    }

    private fun testConversationDisplayInfo(withTtl: Boolean) {
        foreachConvType { conversationId, participants ->
            val messageText = randomMessageText()
            val speaker = participants.first()
            val ttl: Long = if (withTtl) 1 else 0

            val conversationMessageInfo = addMessage(conversationId, speaker, false, messageText, ttl)

            val conversationName = getConversationNameForConversation(conversationId)

            val speakerName = contactsPersistenceManager.get(speaker).get()!!.name

            val message = if (!withTtl)
                conversationMessageInfo.info.message
            else
                null

            val lastMessageData = LastMessageData(
                speakerName,
                speaker,
                message,
                conversationMessageInfo.info.timestamp
            )

            val messageIds = listOf(conversationMessageInfo.info.id)
            val expected = ConversationDisplayInfo(conversationId, conversationName, 1, messageIds, lastMessageData)

            val conversationDisplayInfo = messagePersistenceManager.getConversationDisplayInfo(conversationId).get()

            assertEquals(expected, conversationDisplayInfo, "Invalid display info")
        }
    }

    @Test
    fun `getConversationDisplayInfo should return data when last message data is available`() {
        testConversationDisplayInfo(false)
    }

    @Test
    fun `getConversationDisplayInfo should return a null message when the most recent message is expirable`() {
        testConversationDisplayInfo(true)
    }

    @Test
    fun `markConversationMessagesAsRead should return message ids which are marked as read`() {
        foreachConvType { conversationId, participants ->
            val speaker = participants.first()
            val messageIds = (0..1).map {
                addMessage(conversationId, speaker, false, randomMessageText(), 0).info.id
            }

            val got = messagePersistenceManager.markConversationMessagesAsRead(conversationId, messageIds + listOf(randomMessageId())).get()

            assertThat(got).apply {
                `as`("Should only returned ids marked as read")
                containsOnlyElementsOf(messageIds)
            }
        }
    }

    @Test
    fun `markConversationMessagesAsRead should update the conversation info unread count`() {
        foreachConvType { conversationId, participants ->
            val speaker = participants.first()

            addMessage(conversationId, speaker, false, randomMessageText(), 0).info.id

            val messageIds = listOf(
                addMessage(conversationId, speaker, false, randomMessageText(), 0).info.id
            )

            messagePersistenceManager.markConversationMessagesAsRead(conversationId, messageIds + listOf(randomMessageId())).get()

            val unreadCount = conversationInfoTestUtils.getConversationInfo(conversationId).unreadMessageCount
            assertEquals(1, unreadCount)
        }
    }

    @Test
    fun `getUserConversation should return a conversation for an ALL user`() {
        forUserConvType(AllowedMessageLevel.ALL) {
            assertNotNull(messagePersistenceManager.getUserConversation(it.id).get(), "Expected conversation info")
        }
    }

    @Test
    fun `getUserConversation should return null for a GROUP_ONLY user`() {
        forUserConvType(AllowedMessageLevel.GROUP_ONLY) {
            assertNull(messagePersistenceManager.getUserConversation(it.id).get(), "Expected no conversation info")
        }
    }

    @Test
    fun `getUserConversation should return null for a BLOCKED user`() {
        forUserConvType(AllowedMessageLevel.BLOCKED) {
            assertNull(messagePersistenceManager.getUserConversation(it.id).get(), "Expected no conversation info")
        }
    }

    @Test
    fun `getGroupConversation should return a conversation for a joined group`() {
        MessageTestFixture().run {
            withJoinedGroup { groupId, members ->
                assertNotNull(messagePersistenceManager.getGroupConversation(groupId).get(), "Expected conversation info")
            }
        }
    }

    @Test
    fun `getGroupConversation should return null for a parted group`() {
        MessageTestFixture().run {
            withPartedGroup {
                assertNull(messagePersistenceManager.getGroupConversation(it).get(), "Expected no conversation info")
            }
        }
    }

    @Test
    fun `getGroupConversation should return null for a blocked group`() {
        MessageTestFixture().run {
            withBlockedGroup {
                assertNull(messagePersistenceManager.getGroupConversation(it).get(), "Expected no conversation info")
            }
        }
    }

    @Test
    fun `addFailures should add new failures`() {
        foreachConvType { conversationId, participants ->
            val speaker = participants.first()

            val conversationMessageInfo = addMessage(conversationId, speaker, randomReceivedMessageInfo())
            val failures = mapOf(
                speaker to MessageSendFailure.InactiveUser()
            )

            val messageId = conversationMessageInfo.info.id

            val updatedInfo = messagePersistenceManager.addFailures(conversationId, messageId, failures).get()

            assertThat(updatedInfo.failures).apply {
                `as`("Updated value should contain the new failures")
                containsAllEntriesOf(failures)
            }

            assertEquals(messagePersistenceManager.get(conversationId, messageId).get(), updatedInfo, "Updated info doesn't match persisted info")
        }
    }

    @Test
    fun `addFailures should overwrite old failures for the same participant`() {
        foreachConvType { conversationId, participants ->
            val speaker = participants.first()
            val failures = mapOf(
                speaker to MessageSendFailure.InactiveUser()
            )

            val conversationMessageInfo = addMessage(conversationId, speaker, randomReceivedMessageInfo(), failures)
            val messageId = conversationMessageInfo.info.id

            val newFailures = mapOf(
                speaker to MessageSendFailure.EncryptionFailure()
            )

            val updatedInfo = messagePersistenceManager.addFailures(conversationId, messageId, newFailures).get()
            assertThat(updatedInfo.failures).apply {
                `as`("Updated value should contain the updated failures")
                containsAllEntriesOf(newFailures)
            }
        }
    }

    @Test
    fun `addFailures should fail for invalid message ids`() {
        foreachConvType { conversationId, participants ->
            assertFailsWith(InvalidConversationMessageException::class) {
                messagePersistenceManager.addFailures(conversationId, randomMessageId(), randomMessageSendFailures(participants.first())).get()
            }
        }
    }

    @Test
    fun `addFailures should fail for invalid conversation ids`() {
        assertFailsWithInvalidConversation {
            messagePersistenceManager.addFailures(randomUserConversationId(), randomMessageId(), randomMessageSendFailures(randomUserId())).get()
        }
    }

    @Ignore("maybe")
    @Test
    fun `addFailures should update conversation info`() {
        TODO()
    }
}