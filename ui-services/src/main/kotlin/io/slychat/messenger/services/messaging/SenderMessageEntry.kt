package io.slychat.messenger.services.messaging

import io.slychat.messenger.core.persistence.MessageMetadata

class SenderMessageEntry(val metadata: MessageMetadata, val message: ByteArray)