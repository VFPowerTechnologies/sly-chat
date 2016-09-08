package io.slychat.messenger.core.persistence.sqlite

import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.GroupId
import io.slychat.messenger.core.persistence.GroupInfo
import io.slychat.messenger.core.persistence.sqlite.SQLiteGroupPersistenceManager
import io.slychat.messenger.core.randomGroupInfo

interface GroupPersistenceManagerTestUtils : ContactsPersistenceManagerTestUtils {
    val groupPersistenceManager: SQLiteGroupPersistenceManager

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
}