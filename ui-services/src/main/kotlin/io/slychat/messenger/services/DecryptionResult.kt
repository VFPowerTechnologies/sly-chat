package io.slychat.messenger.services

import io.slychat.messenger.core.UserId
import io.slychat.messenger.services.crypto.MessageDecryptionResult

data class DecryptionResult(val userId: UserId, val result: MessageDecryptionResult)