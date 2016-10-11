package io.slychat.messenger.core.persistence

import io.slychat.messenger.core.UserId

/** Various data used to identify the message.
 *
 * @property category Used to identify the message type for post-send processing.
 */
data class MessageMetadata(
    val userId: UserId,
    val groupId: GroupId?,
    val category: MessageCategory,
    val messageId: String
) {
    init {
        if (category == MessageCategory.TEXT_GROUP && groupId == null)
            throw IllegalArgumentException("groupId must be non-null when category is TEXT_GROUP")

        else if (category == MessageCategory.TEXT_SINGLE && groupId != null)
            throw IllegalArgumentException("groupId must be null when category is TEXT_SINGLE")
    }

    fun getConversationId(): ConversationId = if (groupId != null)
        ConversationId.Group(groupId)
    else
        ConversationId.User(userId)
}