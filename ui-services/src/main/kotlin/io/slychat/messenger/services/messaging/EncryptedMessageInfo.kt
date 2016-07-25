package io.slychat.messenger.services.messaging

import io.slychat.messenger.services.crypto.EncryptedPackagePayloadV0

data class EncryptedMessageInfo(val messageId: String, val payload: EncryptedPackagePayloadV0)