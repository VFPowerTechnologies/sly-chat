package io.slychat.messenger.services.files.cache

import io.slychat.messenger.core.persistence.AttachmentId
import io.slychat.messenger.core.persistence.ReceivedAttachment
import io.slychat.messenger.core.persistence.ReceivedAttachmentState
import java.util.*

class ReceivedAttachments {
    private val all = HashMap<AttachmentId, ReceivedAttachment>()

    private fun updateAttachment(id: AttachmentId, body: (ReceivedAttachment) -> ReceivedAttachment): ReceivedAttachment {
        val attachment = all[id] ?: error("No such attachment: $id")

        val updated = body(attachment)
        all[id] = updated

        return updated
    }

    private fun updateAll(ids: Iterable<AttachmentId>, body: (ReceivedAttachment) -> ReceivedAttachment) {
        ids.forEach {
            updateAttachment(it, body)
        }
    }

    fun get(attachmentId: AttachmentId): ReceivedAttachment? {
        return all[attachmentId]
    }

    fun getPending(): List<ReceivedAttachment> {
        return all.values.filter { it.state == ReceivedAttachmentState.PENDING }
    }

    fun add(attachments: Iterable<ReceivedAttachment>) {
        attachments.forEach {
            val attachmentId = it.id

            all[attachmentId] = it
        }
    }

    //from PENDING only
    fun toMissing(ids: Iterable<AttachmentId>) {
        updateAll(ids) {
            it.copy(state = ReceivedAttachmentState.MISSING)
        }
    }

    fun toComplete(ids: Iterable<AttachmentId>) {
        ids.forEach { id ->
            all.remove(id)
        }
    }
}