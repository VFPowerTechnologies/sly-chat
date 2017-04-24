package io.slychat.messenger.services.files.cache

import io.slychat.messenger.core.persistence.ReceivedAttachment
import nl.komponents.kovenant.Promise
import java.io.InputStream

interface AttachmentCacheManager {
    fun init()

    fun shutdown()

    fun getImageStream(fileId: String): Promise<InputStream?, Exception>

    fun requestCache(receivedAttachments: List<ReceivedAttachment>): Promise<Unit, Exception>
}