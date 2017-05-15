package io.slychat.messenger.services.files.cache

import io.slychat.messenger.core.persistence.AttachmentId
import io.slychat.messenger.core.persistence.ReceivedAttachment
import io.slychat.messenger.core.persistence.ReceivedAttachmentState
import java.util.*

class ReceivedAttachments {
    private val all = HashMap<AttachmentId, ReceivedAttachment>()

    //easier
    private val waitingForSync = HashMap<String, AttachmentId>()

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

            if (it.state == ReceivedAttachmentState.WAITING_ON_SYNC) {
                waitingForSync[it.ourFileId] = attachmentId
            }

            all[attachmentId] = it
        }
    }

    fun getWaitingForSync(fileId: String): ReceivedAttachment? {
        val id = waitingForSync[fileId] ?: return null

        return all[id]
    }

    fun isWaitingForSync(fileId: String): Boolean {
        return fileId in waitingForSync
    }

    //from PENDING only
    fun toMissing(ids: Iterable<AttachmentId>) {
        updateAll(ids) {
            it.copy(state = ReceivedAttachmentState.MISSING)
        }
    }

    fun toWaitingOnSync(ids: Iterable<AttachmentId>) {
        updateAll(ids) {
            waitingForSync[it.ourFileId] = it.id
            it.copy(state = ReceivedAttachmentState.WAITING_ON_SYNC)
        }
    }

    fun toComplete(ids: Iterable<AttachmentId>) {
        ids.forEach { id ->
            val attachment = all[id] ?: error("No such attachment: $id")
            waitingForSync.remove(attachment.ourFileId)
            all.remove(id)
        }
    }
}