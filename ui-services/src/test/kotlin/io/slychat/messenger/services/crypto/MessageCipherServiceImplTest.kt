package io.slychat.messenger.services.crypto

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import io.slychat.messenger.core.SlyAddress
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.crypto.KeyVault
import io.slychat.messenger.core.crypto.generateNewKeyVault
import io.slychat.messenger.core.crypto.generatePrekeys
import io.slychat.messenger.core.http.api.prekeys.*
import io.slychat.messenger.core.relay.base.DeviceMismatchContent
import org.junit.Test
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
    class MockDevice(val keyVault: KeyVault, val id: Int) {
        val registrationId = KeyHelper.generateRegistrationId(false)
        val preKeys = generatePrekeys(keyVault.identityKeyPair, 1, 1, 10)
        //FIXME InMemorySessionStore.getSubDeviceSession never returns 1; in our impl we do, so start device ids from 2
        //instead so we don't run into this behavior
        val signalStore = InMemorySignalProtocolStore(keyVault.identityKeyPair, registrationId)

        private var currentPreKeyId = 1
        private var currentSignedPreKeyId = 1

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
                keyVault.identityKeyPair.publicKey
            )
        }

        fun getPreKeySet(): SerializedPreKeySet {
            val currentPreKey = nextPreKey()

            return SerializedPreKeySet(
                registrationId,
                keyVault.fingerprint,
                serializeSignedPreKey(preKeys.signedPreKey),
                serializePreKey(currentPreKey)
            )
        }
    }

    class MockUser(id: Long, initialDeviceCount: Int = 1) {
        val userId = UserId(id)
        val password = "test"
        val keyVault = generateNewKeyVault(password)

        val devices = (1..initialDeviceCount).map { MockDevice(keyVault, it) }.toMutableList()

        val signalStore: SignalProtocolStore
            get() = devices[0].signalStore

        fun getDevice(deviceId: Int): MockDevice = devices[deviceId-1]
        fun setDevice(deviceId: Int, device: MockDevice) {
            devices[deviceId-1] = device
        }

        fun addDevice(): MockDevice {
            val n = devices.size

            val newDevice = MockDevice(keyVault, n)
            devices.add(newDevice)

            return newDevice
        }

        fun swapDevice(deviceId: Int): MockDevice {
            val newDevice = MockDevice(keyVault, deviceId)
            setDevice(deviceId, newDevice)
            return newDevice
        }

        fun addSessions(target: MockUser, deviceIds: List<Int>) {
            deviceIds.forEach { deviceId ->
                val device = target.devices[deviceId-1]

                val bundle = device.getPreKeyBundle()
                val address = SlyAddress(target.userId, bundle.deviceId).toSignalAddress()
                val builder = SessionBuilder(signalStore, address)
                builder.process(bundle)
            }
        }

        fun getBundles(deviceIds: Collection<Int>): Map<Int, SerializedPreKeySet?> {
            return deviceIds.map { deviceId ->
                val device = devices[deviceId-1]
                deviceId to device.getPreKeySet()
            }.toMap()
        }
    }

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

        val stale = listOf(1)
        val missing = listOf(3)
        val info = DeviceMismatchContent(stale, missing, emptyList())

        val expectedDeviceIds = flatten(stale, missing)
        val client = createPreKeyClientMock(target, expectedDeviceIds)

        val cipherService = createCipherService(client, user)

        cipherService.updateDevices(target.userId, info)

        cipherService.processQueue(false)

        assertSessionsExist(user, target, expectedDeviceIds)
    }

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

        cipherService.updateDevices(target.userId, info)

        cipherService.processQueue(false)

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

        cipherService.updateDevices(target.userId, info)

        cipherService.processQueue(false)

        stale.forEach { deviceId ->
            val address = SlyAddress(target.userId, deviceId)
            val sessionCipher = SessionCipher(user.signalStore, address.toSignalAddress())

            val device = target.getDevice(deviceId)

            assertEquals(device.registrationId, sessionCipher.remoteRegistrationId, "Registration IDs for deviceId=$deviceId don't match")
        }
    }
}