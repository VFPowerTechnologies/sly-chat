package io.slychat.messenger.services.crypto

import io.slychat.messenger.core.SlyAddress
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.http.api.authentication.DeviceInfo
import io.slychat.messenger.core.relay.base.DeviceMismatchContent
import io.slychat.messenger.services.messaging.EncryptedMessageInfo
import io.slychat.messenger.services.messaging.EncryptionResult
import nl.komponents.kovenant.Promise

interface MessageCipherService {
    fun init()

    fun shutdown(join: Boolean)

    fun encrypt(userId: UserId, message: ByteArray, connectionTag: Int): Promise<EncryptionResult, Exception>

    fun decrypt(address: SlyAddress, messages: EncryptedMessageInfo): Promise<DecryptionResult, Exception>

    fun updateDevices(userId: UserId, info: DeviceMismatchContent): Promise<Unit, Exception>

    fun clearDevices(userId: UserId): Promise<Unit, Exception>

    fun updateSelfDevices(otherDevices: List<DeviceInfo>): Promise<Unit, Exception>

    fun addSelfDevice(deviceInfo: DeviceInfo): Promise<Unit, Exception>
}