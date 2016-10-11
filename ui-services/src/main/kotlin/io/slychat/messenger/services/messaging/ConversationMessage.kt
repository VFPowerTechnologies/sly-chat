package io.slychat.messenger.services.messaging

import io.slychat.messenger.core.persistence.ConversationId
import io.slychat.messenger.core.persistence.ConversationMessageInfo

data class ConversationMessage(
    val conversationId: ConversationId,
    val conversationMessageInfo: ConversationMessageInfo
)