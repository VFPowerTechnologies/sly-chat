package io.slychat.messenger.services

import io.slychat.messenger.services.crypto.MessageData

interface EncryptionResult

data class EncryptionOk(val encryptedMessages: List<MessageData>, val connectionTag: Int) : EncryptionResult
data class EncryptionPreKeyFetchFailure(val cause: Throwable): EncryptionResult
data class EncryptionUnknownFailure(val cause: Throwable): EncryptionResult
