package io.slychat.messenger.services.files.cache

import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.ConversationId
import io.slychat.messenger.core.persistence.ReceivedAttachment
import nl.komponents.kovenant.Promise

interface AttachmentService {
    fun init()

    fun shutdown()

    fun addNewReceived(conversationId: ConversationId, sender: UserId, receivedAttachments: List<ReceivedAttachment>)

    fun getImageStream(fileId: String): Promise<ImageLookUpResult, Exception>

    fun getThumbnailStream(fileId: String, resolution: Int): Promise<ImageLookUpResult, Exception>

    fun requestCache(fileIds: List<String>): Promise<Unit, Exception>
}
