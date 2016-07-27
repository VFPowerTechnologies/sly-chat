package io.slychat.messenger.services.contacts

import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.ContactInfo
import io.slychat.messenger.core.persistence.GroupId
import io.slychat.messenger.core.persistence.GroupInfo

sealed class NotificationKey {
    class User(val userId: UserId) : NotificationKey()
    class Group(val groupId: GroupId) : NotificationKey()

    companion object {
        fun idToKey(userId: UserId): String = "U${userId.long}"
        fun idToKey(groupId: GroupId): String = "G${groupId.string}"

        fun keyToId(key: String): NotificationKey {
            val prefix = key[0]
            val actual = key.substring(1)
            return when (prefix) {
                'U' -> NotificationKey.User(UserId(actual.toLong()))
                'G' -> NotificationKey.Group(GroupId(actual))
                else -> throw IllegalArgumentException("Invalid notification key prefix: $prefix")
            }
        }
    }
}

/**
 * @property key Unique identifier to id this particular conversation. Current format is U<userId> or G<groupId>.
 */
data class NotificationConversationInfo(
    val key: String,
    val groupName: String?
) {
    companion object {
        fun from(contactInfo: ContactInfo): NotificationConversationInfo {
            return NotificationConversationInfo(
                NotificationKey.idToKey(contactInfo.id),
                null
            )
        }

        fun from(groupInfo: GroupInfo): NotificationConversationInfo {
            return NotificationConversationInfo(
                NotificationKey.idToKey(groupInfo.id),
                groupInfo.name
            )
        }

    }
}

data class NotificationMessageInfo(
    val speakerName: String,
    val message: String,
    val timestamp: Long
)
