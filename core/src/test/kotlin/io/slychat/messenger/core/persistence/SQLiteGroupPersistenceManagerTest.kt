package io.slychat.messenger.core.persistence

import io.slychat.messenger.core.persistence.sqlite.SQLitePersistenceManager
import io.slychat.messenger.core.persistence.sqlite.loadSQLiteLibraryFromResources
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test

class SQLiteGroupPersistenceManagerTest {
    companion object {
        @JvmStatic
        @BeforeClass
        fun loadLibrary() {
            SQLiteGroupPersistenceManagerTest::class.java.loadSQLiteLibraryFromResources()
        }
    }

    lateinit var persistenceManager: SQLitePersistenceManager
    lateinit var groupPersistenceManager: GroupPersistenceManager

    @Before
    fun before() {
        persistenceManager = SQLitePersistenceManager(null, null, null)
        persistenceManager.init()
        groupPersistenceManager = SQLiteGroupPersistenceManager(persistenceManager)
    }

    @After
    fun after() {
        persistenceManager.shutdown()
    }

    @Test
    fun `getGroupMembers should return all members for the given group`() {}

    @Test
    fun `getGroupMembers should throw InvalidGroupException if the group id is invalid`() {}

    @Test
    fun `getGroupInfo should return info for an existing group`() {}

    @Test
    fun `getGroupInfo should return null for a non-existent group`() {}

    @Test
    fun `getAllGroupConversations should return info only for joined groups`() {}

    @Test
    fun `getAllGroupConversations should throw InvalidGroupException if the group id is invalid`() {}

    @Test
    fun `addMembers should add and return new members to an existing group`() {}

    @Test
    fun `addMembers should only return new members when certain members already exist`() {}

    @Test
    fun `addMembers should throw InvalidGroupException if the group id is invalid`() {}

    @Test
    fun `removeMember should return true and remove the given member if present in an existing group`() {}

    @Test
    fun `removeMember should return false and do nothing if the given member is not present in an existing group`() {}

    @Test
    fun `removeMember should throw InvalidGroupException if the group id is invalid`() {}

    @Test
    fun `isUserMemberOf should return true if the user is part of an existing group`() {}

    @Test
    fun `isUserMemberOf should return false if the user is not part of an existing group`() {}

    @Test
    fun `isUserMemberOf should throw InvalidGroupException if the group id is invalid`() {}

    @Test
    fun `joinGroup should create a new group entry if no info for that group currently exists`() {}

    @Test
    fun `joinGroup should update the membership level to JOINED for a parted group`() {}

    @Test
    fun `joinGroup should do nothing for an already joined group`() {}

    @Test
    fun `partGroup should remove the group log`() {}

    @Test
    fun `partGroup should set the group membership level to PARTED`() {}

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