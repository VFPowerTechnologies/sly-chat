package io.slychat.messenger.services.files.cache

import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.ConversationId
import io.slychat.messenger.core.persistence.ReceivedAttachment

interface AttachmentService {
    fun init()

    fun shutdown()

    fun addNewReceived(conversationId: ConversationId, sender: UserId, messageId: String, receivedAttachments: List<ReceivedAttachment>)
}
