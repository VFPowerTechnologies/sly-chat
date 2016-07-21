package io.slychat.messenger.core.persistence

import io.slychat.messenger.core.*
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

    fun insertRandomContacts(n: Int = 2): Set<UserId> {
        return (1..n).mapToSet { insertRandomContact() }
    }

    fun withJoinedGroup(body: (GroupInfo, members: Set<UserId>) -> Unit) {
        val groupInfo = randomGroupInfo()
        val members = insertRandomContacts()

        groupPersistenceManager.testAddGroupInfo(groupInfo)
        groupPersistenceManager.testAddGroupMembers(groupInfo.id, members)

        body(groupInfo, members)
    }

    fun withEmptyJoinedGroup(body: (GroupInfo) -> Unit) {
        val groupInfo = randomGroupInfo()

        groupPersistenceManager.testAddGroupInfo(groupInfo)

        body(groupInfo)
    }

    @Test
    fun `getGroupMembers should return all members for the given group`() {
        withJoinedGroup { groupInfo, members ->
            val got = groupPersistenceManager.getGroupMembers(groupInfo.id).get()

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
        withJoinedGroup { groupInfo, members ->
            groupPersistenceManager.addMembers(groupInfo.id, members).get()

            val got = groupPersistenceManager.getGroupMembers(groupInfo.id).get()

            assertThat(got).apply {
                `as`("Group members")
                containsOnlyElementsOf(members)
            }
        }
    }

    @Test
    fun `addMembers should only return new members when certain members already exist`() {
        withJoinedGroup { groupInfo, members ->
            val newMembers = insertRandomContacts()

            val toAdd = HashSet(members)
            toAdd.addAll(newMembers)

            val got = groupPersistenceManager.addMembers(groupInfo.id, toAdd).get()

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
        withJoinedGroup { groupInfo, members ->
            val memberList = members.toList()
            val toRemove = memberList.first()
            val remaining = memberList.subList(1, memberList.size)

            val wasRemoved = groupPersistenceManager.removeMember(groupInfo.id, toRemove).get()

            assertTrue(wasRemoved, "Existing user not removed")

            val currentMembers = groupPersistenceManager.getGroupMembers(groupInfo.id).get()

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
        withJoinedGroup { groupInfo, members ->
            members.forEach { member ->
                assertTrue(
                    groupPersistenceManager.isUserMemberOf(groupInfo.id, member).get(),
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
    fun `partGroup should remove the group log`() {}

    @Test
    fun `partGroup should set the group membership level to PARTED`() {}

    @Test
    fun `partGroup should remove the member list for the affected group`() {}

    @Test
    fun `partGroup should do nothing for an already parted group`() {}

    @Test
    fun `partGroup should throw InvalidGroupException if the group id is invalid`() {}

    @Test
    fun `blockGroup should set the group membership level to BLOCKED`() {}

    @Test
    fun `blockGroup should do nothing for an already blocked group`() {}

    @Test
    fun `blockGroup should remove the group log`() {}

    @Test
    fun `blockGroup should throw InvalidGroupException if the group id is invalid`() {}

    @Test
    fun `unblockGroup should set the group membership level to PARTED for a blocked group`() {}

    @Test
    fun `unblockGroup should do nothing for a joined group`() {}

    @Test
    fun `unblockGroup should do nothing for a parted group`() {}

    @Test
    fun `unblockGroup should throw InvalidGroupException if the group id is invalid`() {}

    @Test
    fun `getBlockList should return blocked groups`() {}

    @Test
    fun `getBlockList should return nothing if no groups are blocked`() {}

    @Test
    fun `addMessage should log a message from another user`() {}

    @Test
    fun `addMessage should log a message from yourself`() {}

    @Test
    fun `addMessage should throw InvalidGroupException if the group id is invalid`() {}

    @Test
    fun `deleteMessages should remove the given messages from the group log`() {}

    @Test
    fun `deleteMessages should do nothing if the given messages are not present in the group log`() {}

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