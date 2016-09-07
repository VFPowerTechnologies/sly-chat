package io.slychat.messenger.core.persistence.sqlite

import io.slychat.messenger.core.*
import io.slychat.messenger.core.persistence.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.*
import java.util.*
import kotlin.test.*

class SQLiteMessagePersistenceManagerTest {
    companion object {
        @JvmStatic
        @BeforeClass
        fun loadLibrary() {
            SQLitePreKeyPersistenceManager::class.java.loadSQLiteLibraryFromResources()
        }
    }

    private data class LastConversationInfo(val unreadCount: Int, val lastMessage: String?, val lastTimestamp: Long?)

    lateinit var persistenceManager: SQLitePersistenceManager
    lateinit var contactsPersistenceManager: SQLiteContactsPersistenceManager
    lateinit var messagePersistenceManager: SQLiteMessagePersistenceManager
    lateinit var conversationInfoUtils: ConversationInfoUtils

    @Before
    fun before() {
        persistenceManager = SQLitePersistenceManager(null, null, null)
        persistenceManager.init()
        contactsPersistenceManager = SQLiteContactsPersistenceManager(persistenceManager)
        messagePersistenceManager = SQLiteMessagePersistenceManager(persistenceManager)
        conversationInfoUtils = ConversationInfoUtils(persistenceManager)
    }

    @After
    fun after() {
        persistenceManager.shutdown()
    }

    fun insertRandomMessagesFull(id: GroupId, members: Set<UserId>): List<ConversationMessageInfo> {
        val info = ArrayList<ConversationMessageInfo>()

        members.forEach { member ->
            (1..2).forEach {
                val groupMessageInfo = randomReceivedConversationMessageInfo(member)
                info.add(groupMessageInfo)

                messagePersistenceManager.addMessage(ConversationId(id), groupMessageInfo).get()
            }
        }

        return info
    }

    fun insertRandomSentMessage(id: GroupId): String {
        val groupMessageInfo = randomSentGroupMessageInfo()
        messagePersistenceManager.addMessage(ConversationId(id), groupMessageInfo).get()

        return groupMessageInfo.info.id
    }

    fun insertRandomMessages(id: GroupId, members: Set<UserId>): List<String> {
        return insertRandomMessagesFull(id, members).map { it.info.id }
    }

    fun addRandomContact(allowedMessageLevel: AllowedMessageLevel = AllowedMessageLevel.ALL): UserId {
        val contactInfo = randomContactInfo(allowedMessageLevel)
        contactsPersistenceManager.add(contactInfo).get()
        return contactInfo.id
    }

    fun addMessage(userId: UserId, isSent: Boolean, message: String, ttl: Long): ConversationMessageInfo {
        val conversationMessageInfo = if (isSent)
            ConversationMessageInfo(null, randomSentMessageInfo().copy(message = message, ttl = ttl))
        else
            ConversationMessageInfo(userId, randomReceivedMessageInfo().copy(message = message, ttl = ttl))

        messagePersistenceManager.addMessage(ConversationId(userId), conversationMessageInfo).get()

        return conversationMessageInfo
    }

    fun getMessage(conversationId: ConversationId, messageId: String): ConversationMessageInfo {
        return assertNotNull(messagePersistenceManager.getMessage(conversationId, messageId), "Missing message")
    }
    fun getConversationInfo(conversationId: ConversationId): ConversationInfo {
        return assertNotNull(messagePersistenceManager.getConversationInfo(conversationId).get(), "No last conversation info")
    }

    @Test
    fun `addMessage should add a valid sent message`() {
        val userId = addRandomContact()
        val ttl = 5L

        val messageText = randomMessageText()
        val inserted = addMessage(userId, true, messageText, ttl)

        val got = getMessage(ConversationId(userId), inserted.info.id)
        val messageInfo = got.info

        assertEquals(messageText, messageInfo.message)
        assertEquals(ttl, messageInfo.ttl)
        assertTrue(messageInfo.isSent)
        assertFalse(messageInfo.isDelivered)
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
        val userId = addRandomContact()
        val conversationId = ConversationId(userId)

        val inserted = addMessage(userId, false, randomMessageText(), 0)

        val got = getMessage(conversationId, inserted.info.id)

        assertEquals(inserted.info.message, got.info.message)
        assertFalse(got.info.isSent)
    }

    @Test
    fun `addMessage should update conversation info when inserting a sent message`() {
        val conversationId = ConversationId(addRandomContact())

        val conversationMessageInfo = randomSentGroupMessageInfo()
        messagePersistenceManager.addMessage(conversationId, conversationMessageInfo).get()
        val lastConversationInfo = conversationInfoUtils.getConversationInfo(conversationId)

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

    //FIXME
    /*
    @Test
    fun `addMessage should log a message from another user`() {
        withJoinedGroup { groupId, members ->
            val sender = members.first()
            val groupMessageInfo = randomReceivedConversationMessageInfo(sender)
            messagePersistenceManager.addMessage(groupId, groupMessageInfo).get()

            assertTrue(messagePersistenceManager.internalMessageExists(groupId, groupMessageInfo.info.id), "Message not inserted")
        }
    }

    @Test
    fun `addMessage should log a message from yourself`() {
        withJoinedGroup { groupId, members ->
            val groupMessageInfo = randomReceivedConversationMessageInfo(null)
            messagePersistenceManager.addMessage(groupId, groupMessageInfo).get()

            assertTrue(messagePersistenceManager.internalMessageExists(groupId, groupMessageInfo.info.id), "Message not inserted")
        }
    }

    @Test
    fun `addMessage should update the corresponding group conversation info for a received message`() {
        withJoinedGroup { groupId, members ->
            val sender = members.first()
            val groupMessageInfo = randomReceivedConversationMessageInfo(sender)
            messagePersistenceManager.addMessage(groupId, groupMessageInfo).get()

            val conversationInfo = assertNotNull(messagePersistenceManager.internalGetConversationInfo(groupId), "Missing conversation info")

            assertValidConversationInfo(groupMessageInfo, conversationInfo)
        }
    }

    @Test
    fun `addMessage should update the corresponding group conversation info for a self message`() {
        withJoinedGroup { groupId, members ->
            val groupMessageInfo = randomReceivedConversationMessageInfo(null)
            messagePersistenceManager.addMessage(groupId, groupMessageInfo).get()

            val conversationInfo = assertNotNull(messagePersistenceManager.internalGetConversationInfo(groupId), "Missing conversation info")

            assertValidConversationInfo(groupMessageInfo, conversationInfo, 0)
        }
    }

    @Test
    fun `addMessage should obey insertion order when encountering duplicate timestamps`() {
        withJoinedGroup { groupId, members ->
            val speaker = members.first()
            val first = randomReceivedConversationMessageInfo(speaker)
            val second = ConversationMessageInfo(
                speaker,
                first.info.copy(id = randomMessageId())
            )

            messagePersistenceManager.addMessage(groupId, first).get()
            messagePersistenceManager.addMessage(groupId, second).get()

            val messages = messagePersistenceManager.internalGetAllMessages(groupId)

            assertThat(messages).apply {
                `as`("Group messages")
                containsExactly(first, second)
            }
        }
    }
    */

    @Ignore
    @Test
    fun `addMessage should throw InvalidGroupException if the group id is invalid`() {
        //assertFailsWithInvalidGroup {
            messagePersistenceManager.addMessage(randomGroupConversationId(), randomReceivedConversationMessageInfo(null)).get()
        //}
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
        val got = messagePersistenceManager.getLastMessages(conversationId, 0, 100).get()

        assertEquals(messages, got, "MessageInfo lists don't match")
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
        assertEquals(expected, got)
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

    //FIXME
    /*
    @Test
    fun `getGroupConversationInfo should return info for a joined group`() {
        withJoinedGroup { groupId, members ->
            assertNotNull(messagePersistenceManager.getConversationInfo(groupId).get(), "No returned conversation info")
        }
    }

    @Test
    fun `getConversationInfo should throw InvalidGroupException for a nonexistent group`() {
        assertFailsWithInvalidGroup { messagePersistenceManager.getConversationInfo(randomGroupId()).get() }
    }

    @Test
    fun `getAllConversationInfo should return info only for joined groups`() {
        withJoinedGroup { joinedId, members ->
            withPartedGroup {
                withBlockedGroup {
                    val info = messagePersistenceManager.getAllConversations().get()
                    assertThat(info.map { it.group.id }).apply {
                        `as`("Group conversation info")
                        containsOnly(joinedId)
                    }
                }
            }
        }
    }

    @Test
    fun `getAllConversations should return nothing if no groups are available`() {
        assertTrue(messagePersistenceManager.getAllConversations().get().isEmpty(), "Group list not empty")
    }

    @Test
    fun `deleteMessages should remove the given messages from the group log`() {
        withJoinedGroup { groupId, members ->
            val ids = insertRandomMessages(groupId, members)

            val toRemove = ids.subList(0, 2)
            val remaining = ids.subList(2, ids.size)

            messagePersistenceManager.deleteMessages(groupId, toRemove).get()

            val messages = messagePersistenceManager.internalGetAllMessages(groupId)

            assertThat(messages.map { it.info.id }).apply {
                `as`("Group messages")

                containsOnlyElementsOf(remaining)
            }
        }
    }

    @Test
    fun `deleteMessages should do nothing if the given messages are not present in the group log`() {
        withJoinedGroup { groupId, members ->
            val ids = insertRandomMessages(groupId, members)

            messagePersistenceManager.deleteMessages(groupId, listOf(randomMessageId(), randomMessageId())).get()

            val messages = messagePersistenceManager.internalGetAllMessages(groupId)

            assertThat(messages.map { it.info.id }).apply {
                `as`("Group messages")

                containsOnlyElementsOf(ids)
            }
        }
    }

    @Test
    fun `deleteMessages should update the corresponding group conversation info when some messages remain`() {
        withJoinedGroup { groupId, members ->
            val info = insertRandomMessagesFull(groupId, members)
            val ids = info.map { it.info.id }

            val toRemove = ids.subList(0, 2)

            messagePersistenceManager.deleteMessages(groupId, toRemove).get()

            //should contain the last inserted message
            val convoInfo = assertNotNull(messagePersistenceManager.internalGetConversationInfo(groupId), "Missing group conversation info")

            val lastMessageInfo = info.last()

            assertEquals(lastMessageInfo.speaker, convoInfo.lastSpeaker, "Invalid last speaker")
            assertEquals(lastMessageInfo.info.timestamp, convoInfo.lastTimestamp, "Invalid last time timestamp")
            assertEquals(lastMessageInfo.info.message, convoInfo.lastMessage, "Invalid last message")
        }
    }

    @Test
    fun `deleteMessages should update the corresponding group conversation info when no messages remain`() {
        withJoinedGroup { groupId, members ->
            val ids = insertRandomMessages(groupId, members)

            messagePersistenceManager.deleteMessages(groupId, ids).get()

            assertInitialConversationInfo(groupId)
        }
    }

    @Test
    fun `deleteMessages should throw InvalidGroupException if the group id is invalid`() {
        assertFailsWithInvalidGroup {
            //XXX this won't actually fail for an empty list
            messagePersistenceManager.deleteMessages(randomGroupId(), listOf(randomMessageId())).get()
        }
    }

    @Test
    fun `deleteAllMessages should clear the entire group log`() {
        withJoinedGroup { groupId, members ->
            insertRandomMessages(groupId, members)

            messagePersistenceManager.deleteAllMessages(groupId).get()

            val messages = messagePersistenceManager.internalGetAllMessages(groupId)

            assertThat(messages).apply {
                `as`("Group messages")
                isEmpty()
            }
        }
    }

    @Test
    fun `deleteAllMessages should update the corresponding group conversation info`() {
        withJoinedGroup { groupId, members ->
            insertRandomMessages(groupId, members)

            messagePersistenceManager.deleteAllMessages(groupId).get()

            assertInitialConversationInfo(groupId)
        }
    }

    @Test
    fun `deleteAllMessages should throw InvalidGroupException if the group id is invalid`() {
        withJoinedGroup { groupId, members ->
            insertRandomMessages(groupId, members)

            messagePersistenceManager.deleteAllMessages(groupId).get()

            assertInitialConversationInfo(groupId)
        }
    }

    @Test
    fun `markMessageAsDelivered should update isDelivered and receivedTimestamp fields`() {
        createConvosFor(contact)

        val sentMessageInfo = addMessage(contact, true, testMessage, 0)

        val receivedTimestamp = currentTimestamp() - 1000
        val updatedMessageInfo = messagePersistenceManager.markMessageAsDelivered(contact, sentMessageInfo.id, receivedTimestamp).get()

        assertEquals(receivedTimestamp, updatedMessageInfo.receivedTimestamp)
        assertTrue(updatedMessageInfo.isDelivered)
    }

    @Test
    fun `markMessageAsDelivered should set the given message delivery status to delivered if the message exists`() {
        withJoinedGroup { groupId, members ->
            val id = insertRandomSentMessage(groupId)

            val receivedTimestamp = currentTimestamp() - 1000

            val groupMessageInfo = assertNotNull(messagePersistenceManager.markMessageAsDelivered(groupId, id, receivedTimestamp).get(), "Info wasn't returned")

            assertTrue(groupMessageInfo.info.isDelivered, "Not marked as delivered")
            assertEquals(receivedTimestamp, groupMessageInfo.info.receivedTimestamp, "Received timestamp set to unexpected value")
        }
    }

    @Test
    fun `markMessageAsDelievered should return null if the message has already been marked as delievered`() {
        withJoinedGroup { groupId, members ->
            val id = insertRandomSentMessage(groupId)

            assertNotNull(messagePersistenceManager.markMessageAsDelivered(groupId, id, currentTimestamp()).get(), "Info wasn't returned")
            assertNull(messagePersistenceManager.markMessageAsDelivered(groupId, id, currentTimestamp()).get(), "Message not marked as delievered")
        }
    }

    @Test
    fun `markMessageAsDelivered should throw InvalidGroupMessageException if the given message if the message does not exist`() {
        withJoinedGroup { groupId, members ->
            assertFailsWith(InvalidGroupMessageException::class) {
                messagePersistenceManager.markMessageAsDelivered(groupId, randomMessageId(), currentTimestamp()).get()
            }
        }
    }

    @Test
    fun `markMessageAsDelivered should throw InvalidGroupException if the group id is invalid`() {
        assertFailsWithInvalidGroup { messagePersistenceManager.markMessageAsDelivered(randomGroupId(), randomMessageId(), currentTimestamp()).get() }
    }

    @Test
    fun `markConversationAsRead should reset the unread count`() {
        withJoinedGroup { groupId, members ->
            val convoInfo = GroupConversationInfo(
                groupId,
                members.first(),
                1,
                randomMessageText(),
                currentTimestamp()
            )
            messagePersistenceManager.internalSetConversationInfo(convoInfo)

            messagePersistenceManager.markConversationAsRead(groupId).get()

            val got = assertNotNull(messagePersistenceManager.internalGetConversationInfo(groupId), "Missing conversation info")

            assertEquals(0, got.unreadCount, "Unread count not reset")
        }
    }

    @Test
    fun `markConversationAsRead should throw InvalidGroupException for a non-existent group`() {
        assertFailsWithInvalidGroup { messagePersistenceManager.markConversationAsRead(randomGroupId()).get() }
    }

    @Test
    fun `getLastMessages should return the asked for message range`() {
        withJoinedGroup { groupId, members ->
            val ids = insertRandomMessages(groupId, members)

            val lastMessageIds = messagePersistenceManager.getLastMessages(groupId, 0, 2).get().map { it.info.id }
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
            val lastMessages = messagePersistenceManager.getLastMessages(groupId, 0, 100).get()

            assertThat(lastMessages).apply {
                `as`("Last messages")
                isEmpty()
            }
        }
    }

    @Test
    fun `getLastMessages should throw InvalidGroupException if the group id is invalid`() {
        assertFailsWithInvalidGroup { messagePersistenceManager.getLastMessages(randomGroupId(), 0, 100).get() }
    }

    @Test
    fun `getAllConversations should return an empty list if no conversations are available`() {
        assertTrue(contactsPersistenceManager.getAllConversations().get().isEmpty())
    }
    */

    @Test
    fun `getAllConversations should return conversations for ALL users`() {
        addRandomContact()
        addRandomContact()
        addRandomContact()

        val got = messagePersistenceManager.getAllUserConversations().get()

        assertEquals(3, got.size)
    }

    fun testNoConversations() {
        val got = messagePersistenceManager.getAllUserConversations().get()
        assertThat(got).apply {
            `as`("There should be no conversations")
            isEmpty()
        }
    }

    @Test
    fun `getAllGroupConversations should ignore GROUP_ONLY users`() {
        addRandomContact(AllowedMessageLevel.GROUP_ONLY)

        testNoConversations()
    }

    @Test
    fun `getAllGroupConversations should ignore BLOCKED users`() {
        addRandomContact(AllowedMessageLevel.BLOCKED)

        testNoConversations()
    }

    @Test
    fun `getAllUserConversations should return a last message field if messages are available`() {
        val userId = addRandomContact()
        val userId2 = addRandomContact()

        val lastMessage = randomMessageText()

        conversationInfoUtils.setConversationInfo(
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
    fun `getConversation should return a conversation for an ALL contact`() {
        val id = addRandomContact(AllowedMessageLevel.ALL)

        assertNotNull(messagePersistenceManager.getConversationInfo(ConversationId(id)).get(), "Missing conversation info")
    }

    @Test
    fun `getConversation should return nothing for a GROUP_ONLY contact`() {
        val id = addRandomContact(AllowedMessageLevel.GROUP_ONLY)

        assertNull(messagePersistenceManager.getConversationInfo(ConversationId(id)).get(), "Conversation info should not exist")
    }

    @Test
    fun `getConversation should return nothing for a BLOCKED contact`() {
        val id = addRandomContact(AllowedMessageLevel.BLOCKED)

        assertNull(messagePersistenceManager.getConversationInfo(ConversationId(id)).get(), "Conversation info should not exist")
    }

    //FIXME
    /*
    @Test
    fun `getConversation should include unread message counts in a conversation`() {
        loadContactList()

        val before = assertNotNull(messagePersistenceManager.getConversationInfo(contactA.id).get(), "Missing conversation info")
        assertEquals(0, before.unreadMessageCount)

        setConversationInfo(contactId, 1, testMessage, currentTimestamp())

        val after = assertNotNull(messagePersistenceManager.getConversationInfo(contactA.id).get(), "Missing conversation info")
        assertEquals(1, after.unreadMessageCount)
    }

    @Test
    fun `getConversation should return null if the given conversation doesn't exist`() {
        assertNull(messagePersistenceManager.getConversationInfo(contactA.id).get())
    }

    @Test
    fun `markConversationAsRead should mark all unread messages as read`() {
        loadContactList()

        setConversationInfo(contactId, 2, testMessage, currentTimestamp())

        messagePersistenceManager.markConversationAsRead(contactA.id).get()

        val got = assertNotNull(messagePersistenceManager.getConversationInfo(contactA.id).get(), "Missing conversation info")
        assertEquals(0, got.unreadMessageCount)
    }

    @Test
    fun `markConversationAsRead should throw InvalidConversationException if the given conversation doesn't exist`() {
        assertFailsWith(InvalidConversationException::class) {
            messagePersistenceManager.markConversationAsRead(contactA.id).get()
        }
    }
    */
}