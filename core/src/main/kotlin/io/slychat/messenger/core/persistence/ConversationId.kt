package io.slychat.messenger.core.persistence

import io.slychat.messenger.core.UserId

sealed class ConversationId {
    class User(val id: UserId) : ConversationId()
    class Group(val id: GroupId) : ConversationId()

    companion object {
        operator fun invoke(userId: UserId): ConversationId.User = ConversationId.User(userId)
        operator fun invoke(groupId: GroupId): ConversationId.Group = ConversationId.Group(groupId)
    }
}
