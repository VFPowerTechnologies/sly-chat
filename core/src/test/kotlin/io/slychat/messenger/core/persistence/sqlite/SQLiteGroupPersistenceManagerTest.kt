package io.slychat.messenger.core.persistence.sqlite

import io.slychat.messenger.core.*
import io.slychat.messenger.core.persistence.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import java.util.*
import kotlin.test.*

class SQLiteGroupPersistenceManagerTest : GroupPersistenceManagerTestUtils {
    companion object {
        @JvmStatic
        @BeforeClass
        fun loadLibrary() {
            SQLiteGroupPersistenceManagerTest::class.java.loadSQLiteLibraryFromResources()
        }
    }

    lateinit var persistenceManager: SQLitePersistenceManager
    lateinit override var groupPersistenceManager: SQLiteGroupPersistenceManager
    lateinit override var contactsPersistenceManager: SQLiteContactsPersistenceManager
    lateinit var conversationInfoTestUtils: ConversationInfoTestUtils

    @Before
    fun before() {
        persistenceManager = SQLitePersistenceManager(null, null)
        persistenceManager.init()
        groupPersistenceManager = SQLiteGroupPersistenceManager(persistenceManager)
        contactsPersistenceManager = SQLiteContactsPersistenceManager(persistenceManager)

        conversationInfoTestUtils = ConversationInfoTestUtils(persistenceManager)
    }

    @After
    fun after() {
        persistenceManager.shutdown()
    }

    fun assertGroupInfo(groupId: GroupId, body: (GroupInfo) -> Unit) {
        val got = assertNotNull(groupPersistenceManager.getInfo(groupId).get(), "Missing group info")
        body(got)
    }

    fun assertConvTableExists(groupId: GroupId) {
        persistenceManager.syncRunQuery {
            assertTrue(ConversationTable.exists(it, groupId), "Group conversation table doesn't exist")
        }
    }

    fun assertConvTableNotExists(groupId: GroupId) {
        persistenceManager.syncRunQuery {
            assertFalse(ConversationTable.exists(it, groupId), "Group conversation table exists")
        }
    }

    fun assertFailsWithInvalidGroup(body: () -> Unit) {
        assertFailsWith(InvalidGroupException::class, body)
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
    fun `getNonBlockedMembers should ignore blocked group members`() {
        withJoinedGroup { id, members ->
            val toBlock = members.first()
            val rest = members - toBlock

            contactsPersistenceManager.block(toBlock).get()

            val got = groupPersistenceManager.getNonBlockedMembers(id).get()

            assertThat(got).apply {
                `as`("Should not return blocked members")
                containsOnlyElementsOf(rest)
            }
        }
    }

    @Test
    fun `getNonBlockedMembers should throw InvalidGroupException if the group is invalid`() {
        assertFailsWith(InvalidGroupException::class) {
            groupPersistenceManager.getNonBlockedMembers(randomGroupId()).get()
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

        assertTrue(groupPersistenceManager.join(groupInfo, initialMembers).get())

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

        assertJoined(groupPersistenceManager.join(groupInfo, initialMembers).get())

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

        assertJoined(groupPersistenceManager.join(groupInfo, initialMembers).get())
        assertNotJoined(groupPersistenceManager.join(groupInfo, dupMembers).get())

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

    fun assertJoined(v: Boolean) {
        assertTrue(v, "Should return true for joined groups")
    }

    fun assertNotJoined(v: Boolean) {
        assertFalse(v, "Should return false when a group is already joined")
    }

    @Test
    fun `join should add an empty group conversation info entry`() {
        val groupInfo = randomGroupInfo()

        assertJoined(groupPersistenceManager.join(groupInfo, insertRandomContacts()).get())

        conversationInfoTestUtils.assertInitialConversationInfo(groupInfo.id)
    }

    @Test
    fun `join should create group conversation info for a previously parted group`() {
        withPartedGroupFull {
            val groupInfo = it.copy(membershipLevel = GroupMembershipLevel.JOINED)
            assertJoined(groupPersistenceManager.join(groupInfo, insertRandomContacts()).get())
            conversationInfoTestUtils.assertInitialConversationInfo(it.id)
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

            conversationInfoTestUtils.assertConvTableNotExists(groupId, "Conversation info not removed")
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

            conversationInfoTestUtils.assertConvTableNotExists(groupId, "Conversation info not removed")
        }
    }

    @Test
    fun `block should set the group membership level to BLOCKED for a parted group`() {
        withPartedGroup {
            assertTrue(groupPersistenceManager.block(it).get(), "Should return true when blocking a group")

            val newInfo = assertNotNull(groupPersistenceManager.getInfo(it).get())

            assertEquals(GroupMembershipLevel.BLOCKED, newInfo.membershipLevel, "Group not marked as blocked")
        }
    }

    @Test
    fun `block should do nothing for an already blocked group`() {
        withBlockedGroup {
            assertFalse(groupPersistenceManager.block(it).get(), "Should return false for an already blocked group")
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

    fun testUnblock(groupId: GroupId, expectedWasUnblocked: Boolean, expectedMembershipLevel: GroupMembershipLevel, errorMessage: String) {
        val wasUnblocked = groupPersistenceManager.unblock(groupId).get()

        if (expectedWasUnblocked)
            assertTrue(wasUnblocked, "Should return true when unblocking a group")
        else
            assertFalse(wasUnblocked, "Should return false when a group is already unblocked")

        val newInfo = assertNotNull(groupPersistenceManager.getInfo(groupId).get(), "Missing group")

        assertEquals(expectedMembershipLevel, newInfo.membershipLevel, errorMessage)
    }

    @Test
    fun `unblock should set the group membership level to PARTED for a blocked group`() {
        withBlockedGroup { testUnblock(it, true, GroupMembershipLevel.PARTED, "Membership should be PARTED") }
    }

    @Test
    fun `unblock should do nothing for a joined group`() {
        withJoinedGroup { groupId, members -> testUnblock(groupId, false, GroupMembershipLevel.JOINED, "Membership was modified") }
    }

    @Test
    fun `unblock should do nothing for a parted group`() {
        withPartedGroup { testUnblock(it, false, GroupMembershipLevel.PARTED, "Membership was modified") }
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
    fun `part should remove any associated expiring messages`() {
        withJoinedGroup { groupId, members ->
            val messagePersistenceManager = SQLiteMessagePersistenceManager(persistenceManager)

            val conversationMessageInfo = randomSentConversationMessageInfo()
            val conversationId = groupId.toConversationId()
            messagePersistenceManager.addMessage(conversationId, conversationMessageInfo).get()
            messagePersistenceManager.setExpiration(conversationId, conversationMessageInfo.info.id, 100).get()

            groupPersistenceManager.part(groupId).get()

            assertThat(messagePersistenceManager.getMessagesAwaitingExpiration().get()).apply {
                `as`("Expiring message entries should be removed")
                isEmpty()
            }
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
    fun `block should remove any associated expiring messages`() {
        withJoinedGroup { groupId, members ->
            val messagePersistenceManager = SQLiteMessagePersistenceManager(persistenceManager)

            val conversationMessageInfo = randomSentConversationMessageInfo()
            val conversationId = groupId.toConversationId()
            messagePersistenceManager.addMessage(conversationId, conversationMessageInfo).get()
            messagePersistenceManager.setExpiration(conversationId, conversationMessageInfo.info.id, 100).get()

            groupPersistenceManager.block(groupId).get()

            assertThat(messagePersistenceManager.getMessagesAwaitingExpiration().get()).apply {
                `as`("Expiring message entries should be removed")
                isEmpty()
            }
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

    fun assertMembers(groupId: GroupId, expectedMembers: Set<UserId>) {
        val members = groupPersistenceManager.getMembers(groupId).get()
        assertThat(members).apply {
            `as`("Should contain the given members")
            containsOnlyElementsOf(expectedMembers)
        }
    }

    fun assertNoMembers(groupId: GroupId) {
        val members = groupPersistenceManager.getMembers(groupId).get()
        assertThat(members).apply {
            `as`("Should contain no members")
            isEmpty()
        }
    }

    //I tried to make this test as clear as possible to avoid errors, as testing every transition manually is too tedious
    //and error prone
    fun testApplyDiff(previousLevel: GroupMembershipLevel?, newLevel: GroupMembershipLevel) {
        val groupId = randomGroupId()
        val groupName = randomGroupName()

        val members = if (newLevel == GroupMembershipLevel.JOINED)
            insertRandomContacts()
        else
            emptySet()

        val updates = listOf(
            AddressBookUpdate.Group(groupId, groupName, members, newLevel)
        )

        //insert previous group data
        var previousMembers = emptySet<UserId>()
        if (previousLevel != null) {
            if (previousLevel == GroupMembershipLevel.JOINED)
                previousMembers = insertRandomContacts()

            val info = GroupInfo(groupId, groupName, previousLevel)
            groupPersistenceManager.internalAddInfo(info)
            groupPersistenceManager.internalAddMembers(groupId, previousMembers)
        }

        val transitionOccured = newLevel != previousLevel
        val expectedDelta = if (transitionOccured) {
            when (newLevel) {
                GroupMembershipLevel.BLOCKED -> GroupDiffDelta.Blocked(groupId)
                GroupMembershipLevel.PARTED -> GroupDiffDelta.Parted(groupId)
                GroupMembershipLevel.JOINED -> GroupDiffDelta.Joined(groupId, members)
            }
        }
        else {
            if (newLevel == GroupMembershipLevel.JOINED)
                GroupDiffDelta.MembershipChanged(groupId, members, previousMembers)
            else
                null
        }

        //if we're testing against a joined group, we don't want the conversation info to be reset during the sync
        val convoInfo = if (previousLevel == GroupMembershipLevel.JOINED) {
            val lastSpeaker = insertRandomContact()
            val ci = ConversationInfo(lastSpeaker, 1, randomMessageText(), currentTimestamp())

            conversationInfoTestUtils.setConversationInfo(ConversationId(groupId), ci)

            ci
        }
        else
            ConversationInfo(null, 0, null, null)

        val deltas = groupPersistenceManager.applyDiff(updates).get()

        when (newLevel) {
            GroupMembershipLevel.JOINED -> {
                assertConvTableExists(groupId)
                assertConversationInfo(groupId, convoInfo)
                assertMembers(groupId, members)
            }

            GroupMembershipLevel.PARTED -> {
                assertConvTableNotExists(groupId)
                assertNoMembers(groupId)
            }

            GroupMembershipLevel.BLOCKED -> {
                assertConvTableNotExists(groupId)
                assertNoMembers(groupId)
            }
        }

        val updatedInfo = GroupInfo(groupId, groupName, newLevel)
        assertGroupInfo(groupId) { assertEquals(updatedInfo, it, "Invalid group info") }

        assertNoRemoteUpdates()

        assertThat(deltas).apply {
            `as`("Should return corresponding delta")

            if (expectedDelta != null)
                containsOnly(expectedDelta)
            else
                isEmpty()
        }
    }

    private fun assertConversationInfo(groupId: GroupId, convoInfo: ConversationInfo) {
        val got = conversationInfoTestUtils.getConversationInfo(groupId)

        assertEquals(convoInfo, got, "Invalid conversation info")
    }

    @Test
    fun `applyDiff should add new JOINED groups`() {
        testApplyDiff(
            null,
            GroupMembershipLevel.JOINED
        )
    }

    @Test
    fun `applyDiff should add new PARTED groups`() {
        testApplyDiff(
            null,
            GroupMembershipLevel.PARTED
        )
    }

    @Test
    fun `applyDiff should add new BLOCKED groups`() {
        testApplyDiff(
            null,
            GroupMembershipLevel.BLOCKED
        )
    }

    @Test
    fun `applyDiff should update JOINED to PARTED groups`() {
        testApplyDiff(
            GroupMembershipLevel.JOINED,
            GroupMembershipLevel.PARTED
        )
    }

    @Test
    fun `applyDiff should update JOINED to BLOCKED groups`() {
        testApplyDiff(
            GroupMembershipLevel.JOINED,
            GroupMembershipLevel.BLOCKED
        )
    }

    @Test
    fun `applyDiff should update members and not touch conversation info for JOINED to JOINED`() {
        testApplyDiff(
            GroupMembershipLevel.JOINED,
            GroupMembershipLevel.JOINED
        )
    }

    @Test
    fun `applyDiff should update existing PARTED to JOINED groups`() {
        testApplyDiff(
            GroupMembershipLevel.PARTED,
            GroupMembershipLevel.JOINED
        )
    }

    @Test
    fun `applyDiff should update existing PARTED to BLOCKED groups`() {
        testApplyDiff(
            GroupMembershipLevel.PARTED,
            GroupMembershipLevel.BLOCKED
        )
    }

    @Test
    fun `applyDiff should do nothing for PARTED to PARTED groups`() {
        testApplyDiff(
            GroupMembershipLevel.PARTED,
            GroupMembershipLevel.PARTED
        )
    }

    @Test
    fun `applyDiff should update existing BLOCKED TO PARTED groups`() {
        testApplyDiff(
            GroupMembershipLevel.BLOCKED,
            GroupMembershipLevel.PARTED
        )
    }

    @Test
    fun `applyDiff should update existing BLOCKED TO JOINED groups`() {
        testApplyDiff(
            GroupMembershipLevel.BLOCKED,
            GroupMembershipLevel.JOINED
        )
    }

    @Test
    fun `applyDiff should do nothing for BLOCKED to BLOCKED groups`() {
        testApplyDiff(
            GroupMembershipLevel.BLOCKED,
            GroupMembershipLevel.BLOCKED
        )
    }
}