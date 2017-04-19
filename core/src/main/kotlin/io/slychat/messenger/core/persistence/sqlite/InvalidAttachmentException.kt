package io.slychat.messenger.core.persistence.sqlite

import io.slychat.messenger.core.persistence.AttachmentId

class InvalidAttachmentException(val id: AttachmentId) : RuntimeException("No attachment ${id.n} for ${id.conversationId}/${id.messageId}")