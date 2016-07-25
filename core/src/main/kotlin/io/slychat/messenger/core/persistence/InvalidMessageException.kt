package io.slychat.messenger.core.persistence

import io.slychat.messenger.core.UserId

class InvalidMessageException(val userId: UserId, val messageId: String) : RuntimeException("Invalid message id ($messageId) for user ${userId.long}")
class InvalidGroupMessageException(val groupId: GroupId, val messageId: String) : RuntimeException("Invalid message id ($messageId) for group $groupId")
