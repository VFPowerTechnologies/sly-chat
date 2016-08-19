package io.slychat.messenger.services.messaging

import io.slychat.messenger.services.crypto.MessageData

data class EncryptionResult(val encryptedMessages: List<MessageData>, val connectionTag: Int)
