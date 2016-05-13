package io.slychat.messenger.core.persistence

import io.slychat.messenger.core.UserId

class InvalidConversationException(val userId: UserId) : RuntimeException("No conversation exists for the user id: ${userId.long}")