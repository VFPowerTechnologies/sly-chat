package io.slychat.messenger.core.persistence

import io.slychat.messenger.core.*
import io.slychat.messenger.core.persistence.sqlite.GroupConversationTable
import io.slychat.messenger.core.persistence.sqlite.SQLiteContactsPersistenceManager
import io.slychat.messenger.core.persistence.sqlite.SQLitePersistenceManager
import io.slychat.messenger.core.persistence.sqlite.loadSQLiteLibraryFromResources
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import java.util.*
import kotlin.test.*

class SQLiteGroupPersistenceManagerTest {
    companion object {
        @JvmStatic
        @BeforeClass
        fun loadLibrary() {
            SQLiteGroupPersistenceManagerTest::class.java.loadSQLiteLibraryFromResources()
        }
    }

    lateinit var persistenceManager: SQLitePersistenceManager
    lateinit var groupPersistenceManager: SQLiteGroupPersistenceManager
    lateinit var contactsPersistenceManager: SQLiteContactsPersistenceManager

    @Before
    fun before() {
        persistenceManager = SQLitePersistenceManager(null, null, null)
        persistenceManager.init()
        groupPersistenceManager = SQLiteGroupPersistenceManager(persistenceManager)
        contactsPersistenceManager = SQLiteContactsPersistenceManager(persistenceManager)
    }

    @After
    fun after() {
        persistenceManager.shutdown()
    }

    fun insertRandomContact(): UserId {
        val contactInfo = randomContactInfo()

        contactsPersistenceManager.add(contactInfo).get()

        return contactInfo.id
    }

    /** Randomly generates and creates proper contact entries for users. Required for foreign key constraints. */
    fun insertRandomContacts(n: Int = 2): Set<UserId> {
        return (1..n).mapToSet { insertRandomContact() }
    }

    fun insertRandomMessagesFull(id: GroupId, members: Set<UserId>): List<GroupMessageInfo> {
        val info = ArrayList<GroupMessageInfo>()

        members.forEach { member ->
            (1..2).forEach {
                val groupMessageInfo = randomReceivedGroupMessageInfo(member)
                info.add(groupMessageInfo)

                groupPersistenceManager.addMessage(id, groupMessageInfo).get()
            }
        }

        return info
    }

    fun insertRandomSentMessage(id: GroupId): String {
        val groupMessageInfo = randomSentGroupMessageInfo()
        groupPersistenceManager.addMessage(id, groupMessageInfo).get()

        return groupMessageInfo.info.id
    }

    fun insertRandomMessages(id: GroupId, members: Set<UserId>): List<String> {
        return insertRandomMessagesFull(id, members).map { it.info.id }
    }

    fun withJoinedGroupFull(body: (GroupInfo, members: Set<UserId>) -> Unit) {
        val groupInfo = randomGroupInfo()
        val members = insertRandomContacts()

        groupPersistenceManager.internalAddInfo(groupInfo)
        groupPersistenceManager.internalAddMembers(groupInfo.id, members)

        body(groupInfo, members)
    }

    fun withJoinedGroup(body: (GroupId, members: Set<UserId>) -> Unit) = withJoinedGroupFull {
        groupInfo, members -> body(groupInfo.id, members)
    }

    fun withPartedGroupFull(body: (GroupInfo) -> Unit) {
        val groupInfo = randomGroupInfo(GroupMembershipLevel.PARTED)

        groupPersistenceManager.internalAddInfo(groupInfo)

        body(groupInfo)
    }

    fun withPartedGroup(body: (GroupId) -> Unit) = withPartedGroupFull {
        body(it.id)
    }

    fun withBlockedGroupFull(body: (GroupInfo) -> Unit) {
        val groupInfo = randomGroupInfo(GroupMembershipLevel.BLOCKED)

        groupPersistenceManager.internalAddInfo(groupInfo)

        body(groupInfo)
    }

    fun withBlockedGroup(body: (GroupId) -> Unit) = withBlockedGroupFull {
        body(it.id)
    }

    fun withEmptyJoinedGroup(body: (GroupInfo) -> Unit) {
        val groupInfo = randomGroupInfo()

        groupPersistenceManager.internalAddInfo(groupInfo)

        body(groupInfo)
    }

    fun assertConvTableExists(groupId: GroupId) {
        persistenceManager.syncRunQuery {
            assertTrue(GroupConversationTable.exists(it, groupId), "Group conversation table doesn't exist")
        }
    }

    fun assertConvTableNotExists(groupId: GroupId) {
        persistenceManager.syncRunQuery {
            assertFalse(GroupConversationTable.exists(it, groupId), "Group conversation table exists")
        }
    }

    fun assertFailsWithInvalidGroup(body: () -> Unit) {
        assertFailsWith(InvalidGroupException::class, body)
    }

    fun assertValidConversationInfo(groupMessageInfo: GroupMessageInfo, conversationInfo: GroupConversationInfo, unreadCount: Int = 1) {
        assertEquals(groupMessageInfo.speaker, conversationInfo.lastSpeaker, "Invalid speaker")
        assertEquals(groupMessageInfo.info.message, conversationInfo.lastMessage, "Invalid last message")
        assertEquals(groupMessageInfo.info.timestamp, conversationInfo.lastTimestamp, "Invalid last timestamp")
        assertEquals(unreadCount, conversationInfo.unreadCount, "Invalid unread count")
    }

    @Test
    fun `getList should return the list of all currently joined groups`() {
        withJoinedGroupFull { groupInfo, members ->
            assertThat(groupPersistenceManager.getList().get()).apply {
                `as`("Should return joined groups")
                containsOnly(groupInfo)
            }
        }
    }

    @Test
    fun `getList should ignore parted groups`() {
        withPartedGroup {
            assertThat(groupPersistenceManager.getList().get()).apply {
                `as`("Should ignore parted groups")
                isEmpty()
            }
        }
    }

    @Test
    fun `getList should ignore blocked groups`() {
        withBlockedGroup {
            assertThat(groupPersistenceManager.getList().get()).apply {
                `as`("Should ignore blocked groups")
                isEmpty()
            }
        }
    }

    @Test
    fun `getMembers should return all members for the given group`() {
        withJoinedGroup { id, members ->
            val got = groupPersistenceManager.getMembers(id).get()

            assertThat(got).apply {
                `as`("Group members")
                containsOnlyElementsOf(members)
            }
        }
    }

    @Test
    fun `getMembers should throw InvalidGroupException if the group id is invalid`() {
        assertFailsWith(InvalidGroupException::class) {
            groupPersistenceManager.getMembers(randomGroupId()).get()
        }
    }

    @Test
    fun `getInfo should return info for an existing group`() {
        withEmptyJoinedGroup { groupInfo ->
            val got = groupPersistenceManager.getInfo(groupInfo.id).get()

            assertEquals(groupInfo, got, "Invalid group info")
        }
    }

    @Test
    fun `getInfo should return null for a non-existent group`() {
        assertNull(groupPersistenceManager.getInfo(randomGroupId()).get(), "Got data for nonexistent group")
    }

    @Test
    fun `getConversationInfo should return info for a joined group`() {
        withJoinedGroup { groupId, members ->
            assertNotNull(groupPersistenceManager.getConversationInfo(groupId).get(), "No returned conversation info")
        }
    }

    @Test
    fun `getConversationInfo should return null for a parted group`() {
        withPartedGroup {
            assertNull(groupPersistenceManager.getConversationInfo(it).get(), "Returned conversation info for a parted group")
        }
    }

    @Test
    fun `getConversationInfo should return info for a blocked group`() {
        withBlockedGroup {
            assertNull(groupPersistenceManager.getConversationInfo(it).get(), "Returned conversation info for a blocked group")
        }
    }

    @Test
    fun `getConversationInfo should throw InvalidGroupException for a nonexistent group`() {
        assertFailsWithInvalidGroup { groupPersistenceManager.getConversationInfo(randomGroupId()).get() }
    }

    @Test
    fun `getAllConversationInfo should return info only for joined groups`() {
        withJoinedGroup { joinedId, members ->
            withPartedGroup {
                withBlockedGroup {
                    val info = groupPersistenceManager.getAllConversations().get()
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
        assertTrue(groupPersistenceManager.getAllConversations().get().isEmpty(), "Group list not empty")
    }

    @Test
    fun `addMembers should add and return new members to an existing group`() {
        withJoinedGroup { id, members ->
            groupPersistenceManager.addMembers(id, members).get()

            val got = groupPersistenceManager.getMembers(id).get()

            assertThat(got).apply {
                `as`("Group members")
                containsOnlyElementsOf(members)
            }
        }
    }

    @Test
    fun `addMembers should only return new members when certain members already exist`() {
        withJoinedGroup { id, members ->
            val newMembers = insertRandomContacts()

            val toAdd = HashSet(members)
            toAdd.addAll(newMembers)

            val got = groupPersistenceManager.addMembers(id, toAdd).get()

            assertThat(got).apply {
                `as`("New group members")
                containsOnlyElementsOf(newMembers)
            }
        }
    }

    @Test
    fun `addMembers should throw InvalidGroupException if the group id is invalid`() {
        assertFailsWith(InvalidGroupException::class) {
            groupPersistenceManager.addMembers(randomGroupId(), insertRandomContacts()).get()
        }
    }

    @Test
    fun `removeMember should return true and remove the given member if present in an existing group`() {
        withJoinedGroup { id, members ->
            val memberList = members.toList()
            val toRemove = memberList.first()
            val remaining = memberList.subList(1, memberList.size)

            val wasRemoved = groupPersistenceManager.removeMember(id, toRemove).get()

            assertTrue(wasRemoved, "Existing user not removed")

            val currentMembers = groupPersistenceManager.getMembers(id).get()

            assertThat(currentMembers).apply {
                `as`("Remaining members")
                containsOnlyElementsOf(remaining)
            }
        }
    }

    @Test
    fun `removeMember should return false and do nothing if the given member is not present in an existing group`() {
        withEmptyJoinedGroup { groupInfo ->
            val wasRemoved = groupPersistenceManager.removeMember(groupInfo.id, randomUserId()).get()

            assertFalse(wasRemoved, "Nonexistent user marked as removed")
        }
    }

    @Test
    fun `removeMember should throw InvalidGroupException if the group id is invalid`() {
        assertFailsWithInvalidGroup { groupPersistenceManager.removeMember(randomGroupId(), randomUserId()).get() }
    }

    @Test
    fun `isUserMemberOf should return true if the user is part of an existing group`() {
        withJoinedGroup { id, members ->
            members.forEach { member ->
                assertTrue(
                    groupPersistenceManager.isUserMemberOf(id, member).get(),
                    "User $member is member but not recognized"
                )
            }
        }
    }

    @Test
    fun `isUserMemberOf should return false if the user is not part of an existing group`() {
        withEmptyJoinedGroup { groupInfo ->
            assertFalse(
                groupPersistenceManager.isUserMemberOf(groupInfo.id, randomUserId()).get(),
                "Recognized invalid user as a group member"
            )
        }
    }

    @Test
    fun `isUserMemberOf should throw InvalidGroupException if the group id is invalid`() {
        withJoinedGroup { groupId, members ->
            assertFailsWithInvalidGroup {
                groupPersistenceManager.isUserMemberOf(randomGroupId(), members.first()).get()
            }
        }
    }

    @Test
    fun `join should create a new group entry if no info for that group currently exists`() {
        val groupInfo = randomGroupInfo()
        val initialMembers = insertRandomContacts()

        groupPersistenceManager.join(groupInfo, initialMembers).get()

        val got = assertNotNull(groupPersistenceManager.getInfo(groupInfo.id).get(), "Missing group info")

        assertEquals(groupInfo, got, "Invalid group info")

        val members = groupPersistenceManager.getMembers(groupInfo.id).get()

        assertThat(members).apply {
            `as`("Initial group members")
            containsOnlyElementsOf(initialMembers)
        }
    }

    @Test
    fun `join should update the membership level to JOINED for a parted group`() {
        val groupInfo = randomGroupInfo()
        val initialMembers = insertRandomContacts()

        groupPersistenceManager.internalAddInfo(groupInfo.copy(membershipLevel = GroupMembershipLevel.PARTED))

        groupPersistenceManager.join(groupInfo, initialMembers).get()

        val got = assertNotNull(groupPersistenceManager.getInfo(groupInfo.id).get(), "Missing group info")

        assertEquals(groupInfo, got, "Invalid group info")
    }

    //XXX this should already be empty as part of the parting procedure, so dunno if this is worth testing?
    @Test
    fun `join should overwrite the old member list for a parted group`() {
        val groupInfo = randomGroupInfo()
        val oldMembers = insertRandomContacts()
        val initialMembers = insertRandomContacts()

        groupPersistenceManager.internalAddInfo(groupInfo.copy(membershipLevel = GroupMembershipLevel.PARTED))
        groupPersistenceManager.internalAddMembers(groupInfo.id, oldMembers)

        groupPersistenceManager.join(groupInfo, initialMembers).get()

        val members = groupPersistenceManager.getMembers(groupInfo.id).get()

        assertThat(members).apply {
            `as`("Initial group members")
            containsOnlyElementsOf(initialMembers)
        }
    }

    //make sure member list isn't overwritten
    @Test
    fun `join should do nothing for an already joined group`() {
        val groupInfo = randomGroupInfo()
        val initialMembers = insertRandomContacts()
        val dupMembers = insertRandomContacts()

        groupPersistenceManager.join(groupInfo, initialMembers).get()
        groupPersistenceManager.join(groupInfo, dupMembers).get()

        val members = groupPersistenceManager.getMembers(groupInfo.id).get()

        assertThat(members).apply {
            `as`("Initial group members")
            containsOnlyElementsOf(initialMembers)
        }
    }

    @Test
    fun `join should create the group log`() {
        val groupInfo = randomGroupInfo()

        groupPersistenceManager.join(groupInfo, insertRandomContacts()).get()

        assertConvTableExists(groupInfo.id)
    }

    fun assertInitialConversationInfo(id: GroupId) {
        val conversationInfo = assertNotNull(groupPersistenceManager.internalGetConversationInfo(id), "Missing group conversation info")

        assertEquals(id, conversationInfo.groupId, "Invalid group id")
        assertNull(conversationInfo.lastMessage, "Last message should be empty")
        assertNull(conversationInfo.lastTimestamp, "Last timestamp should be empty")
        assertEquals(0, conversationInfo.unreadCount, "Unread count should be 0")
    }

    @Test
    fun `join should add an empty group conversation info entry`() {
        val groupInfo = randomGroupInfo()

        groupPersistenceManager.join(groupInfo, insertRandomContacts()).get()

        assertInitialConversationInfo(groupInfo.id)
    }

    @Test
    fun `join should create group conversation info for a previously parted group`() {
        withPartedGroupFull {
            groupPersistenceManager.join(it.copy(membershipLevel = GroupMembershipLevel.JOINED), insertRandomContacts()).get()
            assertInitialConversationInfo(it.id)
        }
    }

    @Test
    fun `part should set the group membership level to PARTED`() {
        withJoinedGroup { id, members ->
            val wasParted = groupPersistenceManager.part(id).get()
            assertTrue(wasParted, "Joined group not parted")

            val newInfo = assertNotNull(groupPersistenceManager.getInfo(id).get())

            assertEquals(GroupMembershipLevel.PARTED, newInfo.membershipLevel, "Membership level not updated")
        }
    }

    @Test
    fun `part should remove conversation info for the parted group`() {
        withJoinedGroup { groupId, members ->
            groupPersistenceManager.part(groupId).get()

            assertNull(groupPersistenceManager.getConversationInfo(groupId).get(), "Conversation info not removed")
        }
    }

    @Test
    fun `part should do nothing for an already parted group`() {
        withPartedGroup {
            val wasParted = groupPersistenceManager.part(it).get()
            assertFalse(wasParted, "Parted group was parted")
        }
    }

    @Test
    fun `part should do nothing for a blocked group`() {
        withBlockedGroup {
            val wasParted = groupPersistenceManager.part(it).get()
            assertFalse(wasParted, "Parted group was parted")
        }
    }

    @Test
    fun `part should remove the member list for the affected group`() {
        withJoinedGroup { id, members ->
            groupPersistenceManager.part(id).get()

            assertThat(groupPersistenceManager.getMembers(id).get()).apply {
                `as`("Group members")
                isEmpty()
            }
        }
    }

    @Test
    fun `part should remove the group log`() {
        withJoinedGroup { id, members ->
            groupPersistenceManager.part(id).get()

            assertConvTableNotExists(id)
        }
    }

    @Test
    fun `part should throw InvalidGroupException if the group id is invalid`() {
        assertFailsWithInvalidGroup {
            groupPersistenceManager.part(randomGroupId()).get()
        }
    }

    @Test
    fun `block should set the group membership level to BLOCKED for a joined group`() {
        withJoinedGroup { id, members ->
            groupPersistenceManager.block(id).get()

            val newInfo = assertNotNull(groupPersistenceManager.getInfo(id).get())

            assertEquals(GroupMembershipLevel.BLOCKED, newInfo.membershipLevel, "Group not marked as blocked")
        }
    }

    @Test
    fun `block should remove conversation info for a joined group`() {
        withJoinedGroup { groupId, members ->
            groupPersistenceManager.block(groupId).get()

            assertNull(groupPersistenceManager.getConversationInfo(groupId).get(), "Conversation info not removed")
        }
    }

    @Test
    fun `block should set the group membership level to BLOCKED for a parted group`() {
        withPartedGroup {
            groupPersistenceManager.block(it).get()

            val newInfo = assertNotNull(groupPersistenceManager.getInfo(it).get())

            assertEquals(GroupMembershipLevel.BLOCKED, newInfo.membershipLevel, "Group not marked as blocked")
        }
    }

    @Test
    fun `block should do nothing for an already blocked group`() {
        withBlockedGroup {
            groupPersistenceManager.block(it).get()
        }
    }

    @Test
    fun `block should remove the memberlist for the affected group`() {
        withJoinedGroup { groupId, members ->
            groupPersistenceManager.block(groupId).get()

            assertThat(groupPersistenceManager.getMembers(groupId).get()).apply {
                `as`("Group members")
                isEmpty()
            }
        }
    }

    @Test
    fun `block should remove the group log`() {
        withJoinedGroup { groupId, members ->
            groupPersistenceManager.block(groupId).get()

            assertConvTableNotExists(groupId)
        }
    }

    @Test
    fun `block should throw InvalidGroupException if the group id is invalid`() {
        assertFailsWithInvalidGroup {
            groupPersistenceManager.block(randomGroupId()).get()
        }
    }

    fun testUnblock(groupId: GroupId, expectedMembershipLevel: GroupMembershipLevel, errorMessage: String) {
        groupPersistenceManager.unblock(groupId).get()

        val newInfo = assertNotNull(groupPersistenceManager.getInfo(groupId).get(), "Missing group")

        assertEquals(expectedMembershipLevel, newInfo.membershipLevel, errorMessage)
    }

    @Test
    fun `unblock should set the group membership level to PARTED for a blocked group`() {
        withBlockedGroup { testUnblock(it, GroupMembershipLevel.PARTED, "Membership should be PARTED") }
    }

    @Test
    fun `unblock should do nothing for a joined group`() {
        withJoinedGroup { groupId, members -> testUnblock(groupId, GroupMembershipLevel.JOINED, "Membership was modified") }
    }

    @Test
    fun `unblock should do nothing for a parted group`() {
        withPartedGroup { testUnblock(it, GroupMembershipLevel.PARTED, "Membership was modified") }
    }

    @Test
    fun `unblock should throw InvalidGroupException if the group id is invalid`() {
        assertFailsWithInvalidGroup {
            groupPersistenceManager.unblock(randomGroupId()).get()
        }
    }

    @Test
    fun `getBlockList should return blocked groups`() {
        withBlockedGroup { blockedId ->
            withJoinedGroup { joinedId, members ->
                withPartedGroup { partedId ->
                    val blockedGroups = groupPersistenceManager.getBlockList().get()

                    assertThat(blockedGroups).apply {
                        `as`("Blocked group list")
                        containsOnly(blockedId)
                    }
                }
            }
        }
    }

    @Test
    fun `getBlockList should return nothing if no groups are blocked`() {
        val blockedGroups = groupPersistenceManager.getBlockList().get()

        assertThat(blockedGroups).apply {
            `as`("Blocked group list")
            isEmpty()
        }
    }

    @Test
    fun `addMessage should log a message from another user`() {
        withJoinedGroup { groupId, members ->
            val sender = members.first()
            val groupMessageInfo = randomReceivedGroupMessageInfo(sender)
            groupPersistenceManager.addMessage(groupId, groupMessageInfo).get()

            assertTrue(groupPersistenceManager.internalMessageExists(groupId, groupMessageInfo.info.id), "Message not inserted")
        }
    }

    @Test
    fun `addMessage should log a message from yourself`() {
        withJoinedGroup { groupId, members ->
            val groupMessageInfo = randomReceivedGroupMessageInfo(null)
            groupPersistenceManager.addMessage(groupId, groupMessageInfo).get()

            assertTrue(groupPersistenceManager.internalMessageExists(groupId, groupMessageInfo.info.id), "Message not inserted")
        }
    }

    @Test
    fun `addMessage should update the corresponding group conversation info for a received message`() {
        withJoinedGroup { groupId, members ->
            val sender = members.first()
            val groupMessageInfo = randomReceivedGroupMessageInfo(sender)
            groupPersistenceManager.addMessage(groupId, groupMessageInfo).get()

            val conversationInfo = assertNotNull(groupPersistenceManager.internalGetConversationInfo(groupId), "Missing conversation info")

            assertValidConversationInfo(groupMessageInfo, conversationInfo)
        }
    }

    @Test
    fun `addMessage should update the corresponding group conversation info for a self message`() {
        withJoinedGroup { groupId, members ->
            val groupMessageInfo = randomReceivedGroupMessageInfo(null)
            groupPersistenceManager.addMessage(groupId, groupMessageInfo).get()

            val conversationInfo = assertNotNull(groupPersistenceManager.internalGetConversationInfo(groupId), "Missing conversation info")

            assertValidConversationInfo(groupMessageInfo, conversationInfo, 0)
        }
    }

    @Test
    fun `addMessage should obey insertion order when encountering duplicate timestamps`() {
        withJoinedGroup { groupId, members ->
            val speaker = members.first()
            val first = randomReceivedGroupMessageInfo(speaker)
            val second = GroupMessageInfo(
                speaker,
                first.info.copy(id = randomMessageId())
            )

            groupPersistenceManager.addMessage(groupId, first).get()
            groupPersistenceManager.addMessage(groupId, second).get()

            val messages = groupPersistenceManager.internalGetAllMessages(groupId)

            assertThat(messages).apply {
                `as`("Group messages")
                containsExactly(first, second)
            }
        }
    }

    @Test
    fun `addMessage should throw InvalidGroupException if the group id is invalid`() {
        assertFailsWithInvalidGroup {
            groupPersistenceManager.addMessage(randomGroupId(), randomReceivedGroupMessageInfo(null)).get()
        }
    }

    @Test
    fun `deleteMessages should remove the given messages from the group log`() {
        withJoinedGroup { groupId, members ->
            val ids = insertRandomMessages(groupId, members)

            val toRemove = ids.subList(0, 2)
            val remaining = ids.subList(2, ids.size)

            groupPersistenceManager.deleteMessages(groupId, toRemove).get()

            val messages = groupPersistenceManager.internalGetAllMessages(groupId)

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

            groupPersistenceManager.deleteMessages(groupId, listOf(randomMessageId(), randomMessageId())).get()

            val messages = groupPersistenceManager.internalGetAllMessages(groupId)

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
            val remaining = ids.subList(2, ids.size)

            groupPersistenceManager.deleteMessages(groupId, toRemove).get()

            //should contain the last inserted message
            val convoInfo = assertNotNull(groupPersistenceManager.internalGetConversationInfo(groupId), "Missing group conversation info")

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

            groupPersistenceManager.deleteMessages(groupId, ids).get()

            assertInitialConversationInfo(groupId)
        }
    }

    @Test
    fun `deleteMessages should throw InvalidGroupException if the group id is invalid`() {
        assertFailsWithInvalidGroup {
            //XXX this won't actually fail for an empty list
            groupPersistenceManager.deleteMessages(randomGroupId(), listOf(randomMessageId())).get()
        }
    }

    @Test
    fun `deleteAllMessages should clear the entire group log`() {
        withJoinedGroup { groupId, members ->
            insertRandomMessages(groupId, members)

            groupPersistenceManager.deleteAllMessages(groupId).get()

            val messages = groupPersistenceManager.internalGetAllMessages(groupId)

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

            groupPersistenceManager.deleteAllMessages(groupId).get()

            assertInitialConversationInfo(groupId)
        }
    }

    @Test
    fun `deleteAllMessages should throw InvalidGroupException if the group id is invalid`() {
        withJoinedGroup { groupId, members ->
            insertRandomMessages(groupId, members)

            groupPersistenceManager.deleteAllMessages(groupId).get()

            assertInitialConversationInfo(groupId)
        }
    }

    @Test
    fun `markMessageAsDelivered should set the given message delivery status to delivered if the message exists`() {
        withJoinedGroup { groupId, members ->
            val id = insertRandomSentMessage(groupId)

            groupPersistenceManager.markMessageAsDelivered(groupId, id).get()

            val groupMessageInfo = assertNotNull(groupPersistenceManager.internalGetMessageInfo(groupId, id), "Missing message")
            assertTrue(groupMessageInfo.info.isDelivered, "Not marked as delivered")
            assertTrue(groupMessageInfo.info.receivedTimestamp != 0L, "Received timestamp not updated")
        }
    }

    @Test
    fun `markMessageAsDelivered should throw InvalidGroupMessageException if the given message if the message does not exist`() {
        withJoinedGroup { groupId, members ->
            assertFailsWith(InvalidGroupMessageException::class) {
                groupPersistenceManager.markMessageAsDelivered(groupId, randomMessageId()).get()
            }
        }
    }

    @Test
    fun `markMessageAsDelivered should throw InvalidGroupException if the group id is invalid`() {
        assertFailsWithInvalidGroup { groupPersistenceManager.markMessageAsDelivered(randomGroupId(), randomMessageId()).get() }
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
            groupPersistenceManager.internalSetConversationInfo(convoInfo)

            groupPersistenceManager.markConversationAsRead(groupId).get()

            val got = assertNotNull(groupPersistenceManager.internalGetConversationInfo(groupId), "Missing conversation info")

            assertEquals(0, got.unreadCount, "Unread count not reset")
        }
    }

    @Test
    fun `markConversationAsRead should throw InvalidGroupException for a non-existent group`() {
        assertFailsWithInvalidGroup { groupPersistenceManager.markConversationAsRead(randomGroupId()).get() }
    }

    @Test
    fun `getLastMessages should return the asked for message range`() {
        withJoinedGroup { groupId, members ->
            val ids = insertRandomMessages(groupId, members)

            val lastMessageIds = groupPersistenceManager.getLastMessages(groupId, 0, 2).get().map { it.info.id }
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
            val lastMessages = groupPersistenceManager.getLastMessages(groupId, 0, 100).get()

            assertThat(lastMessages).apply {
                `as`("Last messages")
                isEmpty()
            }
        }
    }

    @Test
    fun `getLastMessages should throw InvalidGroupException if the group id is invalid`() {
        assertFailsWithInvalidGroup { groupPersistenceManager.getLastMessages(randomGroupId(), 0, 100).get() }
    }

    fun assertRemoteUpdates(update: AddressBookUpdate.Group) {
        assertThat(groupPersistenceManager.getRemoteUpdates().get()).apply {
            `as`("Remote updates should not be empty")
            containsOnly(update)
        }
    }

    fun assertNoRemoteUpdates() {
        assertThat(groupPersistenceManager.getRemoteUpdates().get()).apply {
            `as`("Remote updates should be empty")
            isEmpty()
        }
    }

    fun updatefromGroupInfo(groupInfo: GroupInfo, members: Set<UserId>): AddressBookUpdate.Group =
        AddressBookUpdate.Group(groupInfo.id, groupInfo.name, members, groupInfo.membershipLevel)

    fun updatefromGroupInfo(groupInfo: GroupInfo, membershipLevel: GroupMembershipLevel): AddressBookUpdate.Group =
        AddressBookUpdate.Group(groupInfo.id, groupInfo.name, emptySet(), membershipLevel)


    @Test
    fun `join should create a remote update when joining a new group`() {
        val groupInfo = randomGroupInfo()
        val initialMembers = insertRandomContacts()

        val update = updatefromGroupInfo(groupInfo, initialMembers)

        groupPersistenceManager.join(groupInfo, initialMembers).get()

        assertRemoteUpdates(update)
    }

    @Test
    fun `join should create a remote update when rejoining an existing group`() {
        withPartedGroupFull {
            val members = insertRandomContacts()

            val groupInfo = it.copy(membershipLevel = GroupMembershipLevel.JOINED)

            val update = updatefromGroupInfo(groupInfo, members)

            groupPersistenceManager.join(groupInfo, members).get()

            assertRemoteUpdates(update)
        }
    }

    @Test
    fun `join should not create a remote update when joining an already joined group`() {
        withJoinedGroupFull { groupInfo, members ->
            groupPersistenceManager.join(groupInfo, members).get()

            assertNoRemoteUpdates()
        }
    }

    @Test
    fun `part should create a remote update when parting a joined group`() {
        withJoinedGroupFull { groupInfo, members ->
            groupPersistenceManager.part(groupInfo.id).get()

            val update = updatefromGroupInfo(groupInfo, GroupMembershipLevel.PARTED)

            assertRemoteUpdates(update)
        }
    }

    @Test
    fun `part should not create a remote update when parting an already parted group`() {
        withPartedGroup { groupId ->
            groupPersistenceManager.part(groupId).get()

            assertNoRemoteUpdates()
        }
    }

    @Test
    fun `block should create a remote update when blocking an unblocked user`() {
        withJoinedGroupFull { groupInfo, members ->
            groupPersistenceManager.block(groupInfo.id).get()

            val update = updatefromGroupInfo(groupInfo, GroupMembershipLevel.BLOCKED)

            assertRemoteUpdates(update)
        }
    }

    @Test
    fun `block should not create a remote update for an already blocked group`() {
        withBlockedGroup {
            groupPersistenceManager.block(it).get()

            assertNoRemoteUpdates()
        }
    }

    @Test
    fun `unblock should create a remote update when unblocking a blocked group`() {
        withBlockedGroupFull {
            groupPersistenceManager.unblock(it.id).get()

            val update = updatefromGroupInfo(it, GroupMembershipLevel.PARTED)

            assertRemoteUpdates(update)
        }
    }

    @Test
    fun `unblock should not create a remote update for a unblocked group`() {
        withPartedGroup {
            groupPersistenceManager.unblock(it).get()

            assertNoRemoteUpdates()
        }
    }

    @Test
    fun `addMembers should create a remote update when new members are added`() {
        withJoinedGroupFull { groupInfo, initialMembers ->
            val newMembers = insertRandomContacts()
            val allMembers = HashSet(initialMembers)
            allMembers.addAll(newMembers)

            groupPersistenceManager.addMembers(groupInfo.id, newMembers).get()

            val update = updatefromGroupInfo(groupInfo, allMembers)

            assertRemoteUpdates(update)
        }
    }

    @Test
    fun `addMembers should not create a remote update when no new members are added`() {
        withJoinedGroup { groupId, members ->
            groupPersistenceManager.addMembers(groupId, members).get()

            assertNoRemoteUpdates()
        }
    }

    @Test
    fun `removeMember should create a remote update when an existing member is removed`() {
        withJoinedGroupFull { groupInfo, members ->
            val userId = members.first()

            groupPersistenceManager.removeMember(groupInfo.id, userId).get()

            val currentMembers = HashSet(members)
            currentMembers.remove(userId)

            val update = updatefromGroupInfo(groupInfo, currentMembers)

            assertRemoteUpdates(update)
        }
    }

    @Test
    fun `removeMember should not create a remote update for a non-existent member`() {
        withJoinedGroup { groupId, set ->
            groupPersistenceManager.removeMember(groupId, randomUserId()).get()

            assertNoRemoteUpdates()
        }
    }

    @Test
    fun `removeRemoteUpdates should remove the given remote updates`() {
        withBlockedGroup { blockedId ->
            withPartedGroup { partedId ->
                val remoteUpdates = setOf(blockedId, partedId)

                groupPersistenceManager.internalAddRemoteUpdates(remoteUpdates)

                groupPersistenceManager.removeRemoteUpdates(remoteUpdates).get()

                assertNoRemoteUpdates()
            }
        }
    }
}