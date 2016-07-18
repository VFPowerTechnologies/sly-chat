package io.slychat.messenger.services

import io.slychat.messenger.services.crypto.EncryptedPackagePayloadV0

data class EncryptedMessageInfo(val messageId: String, val payload: EncryptedPackagePayloadV0)