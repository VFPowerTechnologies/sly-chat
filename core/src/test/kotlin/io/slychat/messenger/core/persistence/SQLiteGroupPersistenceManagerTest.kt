package io.slychat.messenger.core.persistence

import io.slychat.messenger.core.*
import io.slychat.messenger.core.persistence.sqlite.GroupConversationTable
import io.slychat.messenger.core.persistence.sqlite.SQLiteContactsPersistenceManager
import io.slychat.messenger.core.persistence.sqlite.SQLitePersistenceManager
import io.slychat.messenger.core.persistence.sqlite.loadSQLiteLibraryFromResources
import org.assertj.core.api.Assertions.assertThat
import org.junit.*
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

    fun withJoinedGroupFull(body: (GroupInfo, members: Set<UserId>) -> Unit) {
        val groupInfo = randomGroupInfo()
        val members = insertRandomContacts()

        groupPersistenceManager.testAddGroupInfo(groupInfo)
        groupPersistenceManager.testAddGroupMembers(groupInfo.id, members)

        body(groupInfo, members)
    }

    fun withJoinedGroup(body: (GroupId, members: Set<UserId>) -> Unit) = withJoinedGroupFull {
        groupInfo, members -> body(groupInfo.id, members)
    }

    fun withPartedGroupFull(body: (GroupInfo) -> Unit) {
        val groupInfo = randomGroupInfo(GroupMembershipLevel.PARTED)

        groupPersistenceManager.testAddGroupInfo(groupInfo)

        body(groupInfo)
    }

    fun withPartedGroup(body: (GroupId) -> Unit) = withPartedGroupFull({
        body(it.id)
    })

    fun withBlockedGroupFull(body: (GroupInfo) -> Unit) {
        val groupInfo = randomGroupInfo(GroupMembershipLevel.BLOCKED)

        groupPersistenceManager.testAddGroupInfo(groupInfo)

        body(groupInfo)
    }

    fun withBlockedGroup(body: (GroupId) -> Unit) = withBlockedGroupFull {
        body(it.id)
    }

    fun withEmptyJoinedGroup(body: (GroupInfo) -> Unit) {
        val groupInfo = randomGroupInfo()

        groupPersistenceManager.testAddGroupInfo(groupInfo)

        body(groupInfo)
    }

    fun assertConvTableExists(groupId: GroupId) {
        persistenceManager.syncRunQuery {
            assertTrue(GroupConversationTable.exists(it, groupId), "Group conversation table doesn't exist")
        }
    }

    fun assertConvTableNotExists(groupId: GroupId) {
        persistenceManager.syncRunQuery {
            assertFalse(GroupConversationTable.exists(it, groupId), "Group conversation table doesn't exist")
        }
    }

    fun assertFailsWithInvalidGroup(body: () -> Unit) {
        assertFailsWith(InvalidGroupException::class, body)
    }

    @Test
    fun `getGroupMembers should return all members for the given group`() {
        withJoinedGroup { id, members ->
            val got = groupPersistenceManager.getGroupMembers(id).get()

            assertThat(got).apply {
                `as`("Group members")
                containsOnlyElementsOf(members)
            }
        }
    }

    //TODO
    //maybe change this to just return an empty set; else we'd need to do a group table lookup each time to verify that
    //the group exists, which probably isn't worth it
    @Ignore
    @Test
    fun `getGroupMembers should throw InvalidGroupException if the group id is invalid`() {
        assertFailsWith(InvalidGroupException::class) {
            groupPersistenceManager.getGroupMembers(randomGroupId())
        }
    }

    @Test
    fun `getGroupInfo should return info for an existing group`() {
        withEmptyJoinedGroup { groupInfo ->
            val got = groupPersistenceManager.getGroupInfo(groupInfo.id).get()

            assertEquals(groupInfo, got, "Invalid group info")
        }
    }

    @Test
    fun `getGroupInfo should return null for a non-existent group`() {
        assertNull(groupPersistenceManager.getGroupInfo(randomGroupId()).get(), "Got data for nonexistent group")
    }

    @Test
    fun `getAllGroupConversations should return info only for joined groups`() {}

    @Test
    fun `getAllGroupConversations should throw InvalidGroupException if the group id is invalid`() {}

    @Test
    fun `addMembers should add and return new members to an existing group`() {
        withJoinedGroup { id, members ->
            groupPersistenceManager.addMembers(id, members).get()

            val got = groupPersistenceManager.getGroupMembers(id).get()

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

            val currentMembers = groupPersistenceManager.getGroupMembers(id).get()

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

    //TODO again, would need to check if group table exists
    @Ignore
    @Test
    fun `removeMember should throw InvalidGroupException if the group id is invalid`() {}

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

    //TODO
    @Ignore
    @Test
    fun `isUserMemberOf should throw InvalidGroupException if the group id is invalid`() {}

    @Test
    fun `joinGroup should create a new group entry if no info for that group currently exists`() {
        val groupInfo = randomGroupInfo()
        val initialMembers = insertRandomContacts()

        groupPersistenceManager.joinGroup(groupInfo, initialMembers).get()

        val got = assertNotNull(groupPersistenceManager.getGroupInfo(groupInfo.id).get(), "Missing group info")

        assertEquals(groupInfo, got, "Invalid group info")

        val members = groupPersistenceManager.getGroupMembers(groupInfo.id).get()

        assertThat(members).apply {
            `as`("Initial group members")
            containsOnlyElementsOf(initialMembers)
        }
    }

    @Test
    fun `joinGroup should update the membership level to JOINED for a parted group`() {
        val groupInfo = randomGroupInfo()
        val initialMembers = insertRandomContacts()

        groupPersistenceManager.testAddGroupInfo(groupInfo.copy(membershipLevel = GroupMembershipLevel.PARTED))

        groupPersistenceManager.joinGroup(groupInfo, initialMembers).get()

        val got = assertNotNull(groupPersistenceManager.getGroupInfo(groupInfo.id).get(), "Missing group info")

        assertEquals(groupInfo, got, "Invalid group info")
    }

    //TODO this should already be empty as part of the parting procedure, so dunno if this is worth testing?
    @Test
    fun `joinGroup should overwrite the old member list for a parted group`() {
        val groupInfo = randomGroupInfo()
        val oldMembers = insertRandomContacts()
        val initialMembers = insertRandomContacts()

        groupPersistenceManager.testAddGroupInfo(groupInfo.copy(membershipLevel = GroupMembershipLevel.PARTED))
        groupPersistenceManager.testAddGroupMembers(groupInfo.id, oldMembers)

        groupPersistenceManager.joinGroup(groupInfo, initialMembers).get()

        val members = groupPersistenceManager.getGroupMembers(groupInfo.id).get()

        assertThat(members).apply {
            `as`("Initial group members")
            containsOnlyElementsOf(initialMembers)
        }
    }

    //make sure member list isn't overwritten
    @Test
    fun `joinGroup should do nothing for an already joined group`() {
        val groupInfo = randomGroupInfo()
        val initialMembers = insertRandomContacts()
        val dupMembers = insertRandomContacts()

        groupPersistenceManager.joinGroup(groupInfo, initialMembers).get()
        groupPersistenceManager.joinGroup(groupInfo, dupMembers).get()

        val members = groupPersistenceManager.getGroupMembers(groupInfo.id).get()

        assertThat(members).apply {
            `as`("Initial group members")
            containsOnlyElementsOf(initialMembers)
        }
    }

    @Test
    fun `joinGroup should create the group log`() {
        val groupInfo = randomGroupInfo()

        groupPersistenceManager.joinGroup(groupInfo, insertRandomContacts()).get()

        assertConvTableExists(groupInfo.id)
    }

    fun assertInitialConversationInfo(id: GroupId, groupPersistenceManager: SQLiteGroupPersistenceManager) {
        val conversationInfo = assertNotNull(groupPersistenceManager.testGetGroupConversation(id), "Missing group conversation info")

        assertEquals(id, conversationInfo.groupId, "Invalid group id")
        assertNull(conversationInfo.lastMessage, "Last message should be empty")
        assertNull(conversationInfo.lastTimestamp, "Last timestamp should be empty")
        assertEquals(0, conversationInfo.unreadCount, "Unread count should be 0")
    }

    @Test
    fun `joinGroup should add an empty group conversation info entry`() {
        val groupInfo = randomGroupInfo()

        groupPersistenceManager.joinGroup(groupInfo, insertRandomContacts()).get()

        assertInitialConversationInfo(groupInfo.id, groupPersistenceManager)
    }

    @Test
    fun `joinGroup should reset group conversation info for a previously parted group`() {
        withPartedGroupFull {
            groupPersistenceManager.joinGroup(it.copy(membershipLevel = GroupMembershipLevel.JOINED), insertRandomContacts()).get()
            assertInitialConversationInfo(it.id, groupPersistenceManager)
        }
    }

    @Test
    fun `partGroup should set the group membership level to PARTED`() {
        withJoinedGroup { id, members ->
            val wasParted = groupPersistenceManager.partGroup(id).get()
            assertTrue(wasParted, "Joined group not parted")

            val newInfo = assertNotNull(groupPersistenceManager.getGroupInfo(id).get())

            assertEquals(GroupMembershipLevel.PARTED, newInfo.membershipLevel, "Membership level not updated")
        }
    }

    @Test
    fun `partGroup should do nothing for an already parted group`() {
        withPartedGroup {
            val wasParted = groupPersistenceManager.partGroup(it).get()
            assertFalse(wasParted, "Parted group was parted")
        }
    }

    @Test
    fun `partGroup should do nothing for a blocked group`() {
        withBlockedGroup {
            val wasParted = groupPersistenceManager.partGroup(it).get()
            assertFalse(wasParted, "Parted group was parted")
        }
    }

    @Test
    fun `partGroup should remove the member list for the affected group`() {
        withJoinedGroup { id, members ->
            groupPersistenceManager.partGroup(id).get()

            assertThat(groupPersistenceManager.getGroupMembers(id).get()).apply {
                `as`("Group members")
                isEmpty()
            }
        }
    }

    @Test
    fun `partGroup should remove the group log`() {
        withJoinedGroup { id, members ->
            groupPersistenceManager.partGroup(id).get()

            assertConvTableNotExists(id)
        }
    }

    @Test
    fun `partGroup should throw InvalidGroupException if the group id is invalid`() {
        assertFailsWithInvalidGroup {
            groupPersistenceManager.partGroup(randomGroupId()).get()
        }
    }

    @Test
    fun `blockGroup should set the group membership level to BLOCKED for a joined group`() {
        withJoinedGroup { id, members ->
            groupPersistenceManager.blockGroup(id).get()

            val newInfo = assertNotNull(groupPersistenceManager.getGroupInfo(id).get())

            assertEquals(GroupMembershipLevel.BLOCKED, newInfo.membershipLevel, "Group not marked as blocked")
        }
    }

    @Test
    fun `blockGroup should set the group membership level to BLOCKED for a parted group`() {
        withPartedGroup {
            groupPersistenceManager.blockGroup(it).get()

            val newInfo = assertNotNull(groupPersistenceManager.getGroupInfo(it).get())

            assertEquals(GroupMembershipLevel.BLOCKED, newInfo.membershipLevel, "Group not marked as blocked")
        }
    }

    @Test
    fun `blockGroup should do nothing for an already blocked group`() {
        withBlockedGroup {
            groupPersistenceManager.blockGroup(it).get()
        }
    }

    @Test
    fun `blockGroup should remove the memberlist for the affected group`() {
        withJoinedGroup { groupId, members ->
            groupPersistenceManager.blockGroup(groupId).get()

            assertThat(groupPersistenceManager.getGroupMembers(groupId).get()).apply {
                `as`("Group members")
                isEmpty()
            }
        }
    }

    @Test
    fun `blockGroup should remove the group log`() {
        withJoinedGroup { groupId, members ->
            groupPersistenceManager.blockGroup(groupId).get()

            assertConvTableNotExists(groupId)
        }
    }

    @Test
    fun `blockGroup should throw InvalidGroupException if the group id is invalid`() {
        assertFailsWithInvalidGroup {
            groupPersistenceManager.blockGroup(randomGroupId()).get()
        }
    }

    fun testUnblock(groupId: GroupId, errorMessage: String) {
        groupPersistenceManager.unblockGroup(groupId).get()

        val newInfo = assertNotNull(groupPersistenceManager.getGroupInfo(groupId).get(), "Missing group")

        assertEquals(GroupMembershipLevel.PARTED, newInfo.membershipLevel, errorMessage)
    }

    @Test
    fun `unblockGroup should set the group membership level to PARTED for a blocked group`() {
        withBlockedGroup { testUnblock(it, "Membership should be PARTED") }
    }

    @Test
    fun `unblockGroup should do nothing for a joined group`() {
        withJoinedGroup { groupId, members -> testUnblock(groupId, "Membership was modified") }
    }

    @Test
    fun `unblockGroup should do nothing for a parted group`() {
        withPartedGroup { testUnblock(it, "Membership was modified") }
    }

    @Test
    fun `unblockGroup should throw InvalidGroupException if the group id is invalid`() {
        assertFailsWithInvalidGroup {
            groupPersistenceManager.unblockGroup(randomGroupId()).get()
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

            assertTrue(groupPersistenceManager.testMessageExists(groupId, groupMessageInfo.info.id), "Message not inserted")
        }
    }

    @Test
    fun `addMessage should log a message from yourself`() {
        withJoinedGroup { groupId, members ->
            val groupMessageInfo = randomReceivedGroupMessageInfo(null)
            groupPersistenceManager.addMessage(groupId, groupMessageInfo).get()

            assertTrue(groupPersistenceManager.testMessageExists(groupId, groupMessageInfo.info.id), "Message not inserted")
        }
    }

    fun assertValidConversationInfo(groupMessageInfo: GroupMessageInfo, conversationInfo: GroupConversationInfo, unreadCount: Int = 1) {
        assertEquals(groupMessageInfo.speaker, conversationInfo.lastSpeaker, "Invalid speaker")
        assertEquals(groupMessageInfo.info.message, conversationInfo.lastMessage, "Invalid last message")
        assertEquals(groupMessageInfo.info.timestamp, conversationInfo.lastTimestamp, "Invalid last timestamp")
        assertEquals(unreadCount, conversationInfo.unreadCount, "Invalid unread count")
    }

    @Test
    fun `addMessage should update the corresponding group conversation info for a received message`() {
        withJoinedGroup { groupId, members ->
            val sender = members.first()
            val groupMessageInfo = randomReceivedGroupMessageInfo(sender)
            groupPersistenceManager.addMessage(groupId, groupMessageInfo).get()

            val conversationInfo = assertNotNull(groupPersistenceManager.testGetGroupConversation(groupId), "Missing conversation info")

            assertValidConversationInfo(groupMessageInfo, conversationInfo)
        }
    }

    @Test
    fun `addMessage should update the corresponding group conversation info for a self message`() {
        withJoinedGroup { groupId, members ->
            val groupMessageInfo = randomReceivedGroupMessageInfo(null)
            groupPersistenceManager.addMessage(groupId, groupMessageInfo).get()

            val conversationInfo = assertNotNull(groupPersistenceManager.testGetGroupConversation(groupId), "Missing conversation info")

            assertValidConversationInfo(groupMessageInfo, conversationInfo, 0)
        }
    }

    @Test
    fun `addMessage should throw InvalidGroupException if the group id is invalid`() {
        assertFailsWithInvalidGroup {
            groupPersistenceManager.addMessage(randomGroupId(), randomReceivedGroupMessageInfo(null)).get()
        }
    }

    @Test
    fun `deleteMessages should remove the given messages from the group log`() {}

    @Test
    fun `deleteMessages should do nothing if the given messages are not present in the group log`() {}

    @Test
    fun `deleteMessages should update the corresponding group conversation info`() {}

    @Test
    fun `deleteMessages should throw InvalidGroupException if the group id is invalid`() {}

    @Test
    fun `deleteAllMessages should clear the entire group log`() {}

    @Test
    fun `deleteAllMessages should throw InvalidGroupException if the group id is invalid`() {}

    @Test
    fun `markMessageAsDelivered should set the given message delivery status to delivered if the message exists`() {}

    //I guess?
    @Test
    fun `markMessageAsDelivered should do nothing if the given message if the message does not exist`() {}

    @Test
    fun `markMessageAsDelivered should throw InvalidGroupException if the group id is invalid`() {}

    @Test
    fun `getLastMessages should return the asked for message range`() {}

    @Test
    fun `getLastMessages should return nothing if the range does not exist`() {}

    @Test
    fun `getLastMessages should throw InvalidGroupException if the group id is invalid`() {}
}