package io.slychat.messenger.services.messaging

import io.slychat.messenger.core.persistence.MessageMetadata

data class MessageSendRecord(val metadata: MessageMetadata, val serverReceivedTimestamp: Long)