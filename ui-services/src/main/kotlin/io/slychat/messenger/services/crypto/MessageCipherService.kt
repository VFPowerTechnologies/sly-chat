package io.slychat.messenger.services.crypto

import io.slychat.messenger.core.SlyAddress
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.relay.base.DeviceMismatchContent
import io.slychat.messenger.services.DecryptionResult
import io.slychat.messenger.services.EncryptedMessageInfo
import io.slychat.messenger.services.EncryptionResult
import rx.Observable

interface MessageCipherService {
    val encryptedMessages: Observable<EncryptionResult>
    val decryptedMessages: Observable<DecryptionResult>
    val deviceUpdates: Observable<DeviceUpdateResult>

    fun start()
    fun shutdown(join: Boolean)
    fun encrypt(userId: UserId, message: ByteArray, connectionTag: Int)
    fun decrypt(address: SlyAddress, messages: List<EncryptedMessageInfo>)
    fun updateDevices(userId: UserId, info: DeviceMismatchContent)
}