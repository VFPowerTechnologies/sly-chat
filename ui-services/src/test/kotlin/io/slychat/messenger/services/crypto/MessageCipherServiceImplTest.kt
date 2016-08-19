package io.slychat.messenger.services.crypto

import com.nhaarman.mockito_kotlin.*
import io.slychat.messenger.core.SlyAddress
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.crypto.addPreKeysToStore
import io.slychat.messenger.core.crypto.generateKeyPair
import io.slychat.messenger.core.crypto.generatePrekeys
import io.slychat.messenger.core.crypto.identityKeyFingerprint
import io.slychat.messenger.core.http.api.prekeys.*
import io.slychat.messenger.core.randomSerializedMessage
import io.slychat.messenger.core.randomUUID
import io.slychat.messenger.core.relay.base.DeviceMismatchContent
import io.slychat.messenger.services.messaging.EncryptedMessageInfo
import io.slychat.messenger.services.messaging.EncryptionOk
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.whispersystems.libsignal.IdentityKeyPair
import org.whispersystems.libsignal.SessionBuilder
import org.whispersystems.libsignal.SessionCipher
import org.whispersystems.libsignal.state.PreKeyBundle
import org.whispersystems.libsignal.state.PreKeyRecord
import org.whispersystems.libsignal.state.SignalProtocolStore
import org.whispersystems.libsignal.state.impl.InMemorySignalProtocolStore
import org.whispersystems.libsignal.util.KeyHelper
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MessageCipherServiceImplTest {
    companion object {
        //InMemorySessionStore.getSubDeviceSession never returns 1; in our impl we do (we have no master device concept),
        //so start device ids from 2 instead so we don't run into this behavior
        const val initialDeviceId = 2
    }

    class MockDevice(val identityKeyPair: IdentityKeyPair, val id: Int) {
        val registrationId = KeyHelper.generateRegistrationId(false)
        val preKeys = generatePrekeys(identityKeyPair, 1, 1, 10)
        val signalStore = InMemorySignalProtocolStore(identityKeyPair, registrationId)

        private var currentPreKeyId = 1
        private val currentSignedPreKeyId = 1

        init {
            addPreKeysToStore(signalStore, preKeys)
        }

        private fun nextPreKey(): PreKeyRecord {
            val currentPreKey = preKeys.oneTimePreKeys[currentPreKeyId]

            currentPreKeyId += 1

            return currentPreKey
        }

        fun getPreKeyBundle(): PreKeyBundle {
            val currentPreKey = nextPreKey()

            return PreKeyBundle(
                registrationId,
                id,
                currentPreKey.id,
                currentPreKey.keyPair.publicKey,
                currentSignedPreKeyId,
                preKeys.signedPreKey.keyPair.publicKey,
                preKeys.signedPreKey.signature,
                identityKeyPair.publicKey
            )
        }

        fun getPreKeySet(): SerializedPreKeySet {
            val currentPreKey = nextPreKey()

            return SerializedPreKeySet(
                registrationId,
                //this isn't the format we actually use but it doesn't matter for the purposes of testing
                identityKeyFingerprint(identityKeyPair.publicKey),
                serializeSignedPreKey(preKeys.signedPreKey),
                serializePreKey(currentPreKey)
            )
        }
    }

    class MockUser(id: Long, initialDeviceCount: Int = 1) {
        val userId = UserId(id)
        val identityKeyPair = generateKeyPair()

        val devices = (0..initialDeviceCount - 1).map { createDevice(it + initialDeviceId) }.toMutableList()
        val deviceIds = devices.map { it.id }

        /** Shortcut for accessing the first device's SignalProtocolStore. */
        val signalStore: SignalProtocolStore
            get() = devices[0].signalStore

        val firstDevice: MockDevice = devices[0]

        fun getDevice(deviceId: Int): MockDevice = devices[deviceId - initialDeviceId]
        private fun setDevice(deviceId: Int, device: MockDevice) {
            devices[deviceId - initialDeviceId] = device
        }

        private fun createDevice(deviceId: Int): MockDevice {
            val newDevice = MockDevice(identityKeyPair, deviceId)
            return newDevice
        }

        /** Swaps the device with the given ID with a newly created device. */
        fun swapDevice(deviceId: Int): MockDevice {
            val newDevice = MockDevice(identityKeyPair, deviceId)
            setDevice(deviceId, newDevice)
            return newDevice
        }

        fun addSessions(target: MockUser, deviceIds: List<Int>) {
            deviceIds.forEach { deviceId ->
                val device = target.getDevice(deviceId)

                val bundle = device.getPreKeyBundle()
                val address = SlyAddress(target.userId, bundle.deviceId).toSignalAddress()
                val builder = SessionBuilder(signalStore, address)
                builder.process(bundle)
            }
        }

        fun getBundles(deviceIds: Collection<Int>): Map<Int, SerializedPreKeySet?> {
            return if (deviceIds.isEmpty()) {
                devices.mapIndexed { i, mockDevice ->
                    i to mockDevice.getPreKeySet()
                }
            }
            else {
                deviceIds.map { deviceId ->
                    val device = getDevice(deviceId)
                    deviceId to device.getPreKeySet()
                }
            }.toMap()
        }
    }

    val connectionTag = 0

    fun <T> flatten(vararg lists: List<T>): List<T> {
        val r = ArrayList<T>()

        lists.forEach { r.addAll(it) }

        return r
    }

    fun createPreKeyClientMock(target: MockUser, expectedDeviceIds: List<Int>): PreKeyClient {
        val client = mock<PreKeyClient>()

        val request = PreKeyRetrievalRequest(target.userId, expectedDeviceIds)
        val response = PreKeyRetrievalResponse(null, target.getBundles(expectedDeviceIds))

        whenever(client.retrieve(any(), eq(request))).thenReturn(response)

        return client
    }

    fun createCipherService(client: PreKeyClient, user: MockUser): MessageCipherServiceImpl {
        val authTokenManager = MockAuthTokenManager()
        return MessageCipherServiceImpl(authTokenManager, client, user.signalStore)
    }

    fun assertSessionStatus(signalProtocolStore: SignalProtocolStore, target: MockUser, expectedDeviceIds: List<Int>, exists: Boolean) {
        expectedDeviceIds.forEach { deviceId ->
            val address = SlyAddress(target.userId, deviceId)
            val actual = signalProtocolStore.containsSession(address.toSignalAddress())

            if (exists)
                assertTrue(actual, "Session doesn't exist for deviceId=$deviceId")
            else
                assertFalse(actual, "Session exists for deviceId=$deviceId")
        }
    }

    fun assertSessionsExist(user: MockUser, target: MockUser, expectedDeviceIds: List<Int>) {
        assertSessionStatus(user.signalStore, target, expectedDeviceIds, true)
    }

    fun assertSessionsNotExist(user: MockUser, target: MockUser, expectedDeviceIds: List<Int>) {
        assertSessionStatus(user.signalStore, target, expectedDeviceIds, false)
    }

    @Test
    fun `given device info, it should retrieve prekey info for stale and missing devices`() {
        val user = MockUser(1)

        val target = MockUser(2, 3)

        val stale = listOf(2)
        val missing = listOf(4)
        val info = DeviceMismatchContent(stale, missing, emptyList())

        val expectedDeviceIds = flatten(stale, missing)
        val client = createPreKeyClientMock(target, expectedDeviceIds)

        val cipherService = createCipherService(client, user)

        cipherService.updateDevices(target.userId, info)

        cipherService.processQueue(false)

        assertSessionsExist(user, target, expectedDeviceIds)
    }

    @Test
    fun `it should fetch remote key data for all of a user's device without a session when encryption is requested`() {
        val user = MockUser(1)
        val recipient = MockUser(2)

        val client = createPreKeyClientMock(recipient, emptyList())

        val cipherService = createCipherService(client, user)

        val p = cipherService.encrypt(recipient.userId, randomSerializedMessage(), connectionTag)

        cipherService.processQueue(false)

        val result = p.get()
        assertThat(result).isInstanceOf(EncryptionOk::class.java).`as`("Encryption failure")
    }

    @Test
    fun `it should not fetch remote key data when a user has an existing session`() {
        val user = MockUser(1)
        val recipient = MockUser(2)

        user.addSessions(recipient, recipient.deviceIds)

        val client = mock<PreKeyClient>()

        val cipherService = createCipherService(client, user)

        val p = cipherService.encrypt(recipient.userId, randomSerializedMessage(), connectionTag)

        cipherService.processQueue(false)

        p.get()

        verify(client, never()).retrieve(any(), any())
    }

    //tests PreKeyWhisper messages
    @Test
    fun `decryption should success the receiver has no existing session`() {
        val sender = MockUser(1)
        val receiver = MockUser(2)

        sender.addSessions(receiver, receiver.deviceIds)

        val originalMessage = randomSerializedMessage()

        //first we create the message to send
        val senderService = createCipherService(mock(), sender)

        val encryptionResult = senderService.encrypt(receiver.userId, originalMessage, connectionTag)
        senderService.processQueue(false)
        val encryptedMessage = (encryptionResult.get() as EncryptionOk).encryptedMessages[0]

        assertTrue(encryptedMessage.payload.isPreKeyWhisper, "Not a PreKeyWhisper")

        val senderAddress = SlyAddress(sender.userId, initialDeviceId)

        //then we attempt to decrypt it on the other end
        val receiverService = createCipherService(mock(), receiver)
        val encryptedMessageInfo = EncryptedMessageInfo(
            randomUUID(),
            encryptedMessage.payload
        )

        val decryptionResult = receiverService.decrypt(senderAddress, encryptedMessageInfo)
        receiverService.processQueue(false)
        val decryptedMessage = (decryptionResult.get().result as MessageDecryptionResult.Success).data

        assertThat(decryptedMessage).isEqualTo(originalMessage).`as`("Decrypted should match original")
    }

    //XXX if you actually delete a session on the receiver side, and the sender still believes it exists and sends
    //a message, you get a NPE during decryption
    //however in normal usage this can't occur

    @Test
    fun `given device info, it should delete removed device sessions`() {
        val user = MockUser(1)

        val target = MockUser(2, 3)

        val removed = listOf(3)
        val info = DeviceMismatchContent(emptyList(), emptyList(), removed)

        //should not be called
        val client = mock<PreKeyClient>()
        whenever(client.retrieve(any(), any())).thenThrow(AssertionError("retrieve called"))

        user.addSessions(target, removed)

        val cipherService = createCipherService(client, user)

        val p = cipherService.updateDevices(target.userId, info)

        cipherService.processQueue(false)

        p.get()

        assertSessionsNotExist(user, target, removed)
    }

    @Test
    fun `given device info, it should refresh stale devices`() {
        val user = MockUser(1)

        val target = MockUser(2, 3)

        val stale = listOf(3)
        val info = DeviceMismatchContent(stale, emptyList(), emptyList())

        user.addSessions(target, stale)

        stale.forEach { target.swapDevice(it) }

        val client = createPreKeyClientMock(target, stale)

        val cipherService = createCipherService(client, user)

        val p = cipherService.updateDevices(target.userId, info)

        cipherService.processQueue(false)

        p.get()

        stale.forEach { deviceId ->
            val address = SlyAddress(target.userId, deviceId)
            val sessionCipher = SessionCipher(user.signalStore, address.toSignalAddress())

            val device = target.getDevice(deviceId)

            assertEquals(device.registrationId, sessionCipher.remoteRegistrationId, "Registration IDs for deviceId=$deviceId don't match")
        }
    }
}