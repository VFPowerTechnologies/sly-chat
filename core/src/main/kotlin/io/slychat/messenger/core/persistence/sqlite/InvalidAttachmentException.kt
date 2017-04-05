package io.slychat.messenger.core.persistence.sqlite

import io.slychat.messenger.core.persistence.ConversationId

class InvalidAttachmentException(val conversationId: ConversationId, val messageId: String, val n: Int) : RuntimeException("No attachment $n for $conversationId/$messageId")