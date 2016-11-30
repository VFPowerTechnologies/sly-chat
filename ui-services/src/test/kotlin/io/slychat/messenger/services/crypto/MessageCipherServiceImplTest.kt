package io.slychat.messenger.services.crypto

import com.nhaarman.mockito_kotlin.*
import io.slychat.messenger.core.SlyAddress
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.crypto.generateKeyPair
import io.slychat.messenger.core.crypto.identityKeyFingerprint
import io.slychat.messenger.core.crypto.randomRegistrationId
import io.slychat.messenger.core.crypto.randomUUID
import io.slychat.messenger.core.crypto.signal.addPreKeysToStore
import io.slychat.messenger.core.crypto.signal.generatePrekeys
import io.slychat.messenger.core.http.api.authentication.DeviceInfo
import io.slychat.messenger.core.http.api.prekeys.*
import io.slychat.messenger.core.persistence.LogEvent
import io.slychat.messenger.core.persistence.LogTarget
import io.slychat.messenger.core.persistence.SecurityEventData
import io.slychat.messenger.core.randomSerializedMessage
import io.slychat.messenger.core.relay.base.DeviceMismatchContent
import io.slychat.messenger.services.MockEventLogService
import io.slychat.messenger.services.crypto.MessageCipherServiceImpl.Companion.deviceDiff
import io.slychat.messenger.services.messaging.EncryptedMessageInfo
import io.slychat.messenger.testutils.KovenantTestModeRule
import io.slychat.messenger.testutils.withTimeAs
import org.assertj.core.api.Assertions.assertThat
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.whispersystems.libsignal.*
import org.whispersystems.libsignal.state.*
import org.whispersystems.libsignal.state.impl.InMemoryIdentityKeyStore
import org.whispersystems.libsignal.state.impl.InMemoryPreKeyStore
import org.whispersystems.libsignal.state.impl.InMemorySignedPreKeyStore
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MessageCipherServiceImplTest {
    companion object {
        //InMemorySessionStore.getSubDeviceSession never returns 1; in our impl we do (we have no master device concept),
        //so start device ids from 2 instead so we don't run into this behavior
        const val initialDeviceId = 2

        @JvmField
        @ClassRule
        val kovenantTestMode = KovenantTestModeRule()
    }

    @Rule
    @JvmField
    val timeoutRule = Timeout(1000)

    //InMemorySessionStore.deleteAllSessions is bugged in 2.2.0 since it attempts to remove items during iteration
    private class FixedInMemorySessionStore : SessionStore {
        private val sessions = HashMap<SignalProtocolAddress, ByteArray>()

        override fun getSubDeviceSessions(name: String): List<Int> = synchronized(this) {
            sessions.map { it.key.deviceId }
        }

        override fun deleteAllSessions(name: String) = synchronized(this) {
            val iter = sessions.iterator()
            while (iter.hasNext()) {
                val addr = iter.next()
                if (addr.key.name == name)
                    iter.remove()
            }
        }

        override fun containsSession(address: SignalProtocolAddress): Boolean = synchronized(this) {
            address in sessions
        }

        override fun loadSession(address: SignalProtocolAddress): SessionRecord = synchronized(this) {
            return sessions[address]?.let(::SessionRecord) ?: SessionRecord()
        }

        override fun deleteSession(address: SignalProtocolAddress) = synchronized(this) {
            sessions.remove(address)
            Unit
        }

        override fun storeSession(address: SignalProtocolAddress, record: SessionRecord) = synchronized(this) {
            sessions[address] = record.serialize()
        }
    }

    private class InMemorySignalProtocolStore(identityKeyPair: IdentityKeyPair, registrationId: Int) : SignalProtocolStore {
        private val identityStore = InMemoryIdentityKeyStore(identityKeyPair, registrationId)
        private val preKeyStore = InMemoryPreKeyStore()
        private val signedPreKeyStore = InMemorySignedPreKeyStore()
        private val sessionStore = FixedInMemorySessionStore()

        override fun containsSession(address: SignalProtocolAddress): Boolean {
            return sessionStore.containsSession(address)
        }

        override fun deleteSession(address: SignalProtocolAddress) {
            return sessionStore.deleteSession(address)
        }

        override fun getSubDeviceSessions(name: String): List<Int> {
            return sessionStore.getSubDeviceSessions(name)
        }

        override fun deleteAllSessions(name: String) {
            return sessionStore.deleteAllSessions(name)
        }

        override fun loadSession(address: SignalProtocolAddress): SessionRecord {
            return sessionStore.loadSession(address)
        }

        override fun storeSession(address: SignalProtocolAddress, record: SessionRecord) {
            return sessionStore.storeSession(address, record)
        }

        override fun removeSignedPreKey(signedPreKeyId: Int) {
            return signedPreKeyStore.removeSignedPreKey(signedPreKeyId)
        }

        override fun containsSignedPreKey(signedPreKeyId: Int): Boolean {
            return signedPreKeyStore.containsSignedPreKey(signedPreKeyId)
        }

        override fun storeSignedPreKey(signedPreKeyId: Int, record: SignedPreKeyRecord) {
            return signedPreKeyStore.storeSignedPreKey(signedPreKeyId, record)
        }

        override fun loadSignedPreKey(signedPreKeyId: Int): SignedPreKeyRecord {
            return signedPreKeyStore.loadSignedPreKey(signedPreKeyId)
        }

        override fun loadSignedPreKeys(): MutableList<SignedPreKeyRecord> {
            return signedPreKeyStore.loadSignedPreKeys()
        }

        override fun removePreKey(preKeyId: Int) {
            return preKeyStore.removePreKey(preKeyId)
        }

        override fun loadPreKey(preKeyId: Int): PreKeyRecord {
            return preKeyStore.loadPreKey(preKeyId)
        }

        override fun storePreKey(preKeyId: Int, record: PreKeyRecord) {
            return preKeyStore.storePreKey(preKeyId, record)
        }

        override fun containsPreKey(preKeyId: Int): Boolean {
            return preKeyStore.containsPreKey(preKeyId)
        }

        override fun saveIdentity(address: SignalProtocolAddress, identityKey: IdentityKey) {
            return identityStore.saveIdentity(address, identityKey)
        }

        override fun getIdentityKeyPair(): IdentityKeyPair {
            return identityStore.identityKeyPair
        }

        override fun isTrustedIdentity(address: SignalProtocolAddress, identityKey: IdentityKey): Boolean {
            return identityStore.isTrustedIdentity(address, identityKey)
        }

        override fun getLocalRegistrationId(): Int {
            return identityStore.localRegistrationId
        }
    }

    private class MockDevice(val identityKeyPair: IdentityKeyPair, val id: Int) {
        val registrationId = randomRegistrationId()
        val preKeys = generatePrekeys(identityKeyPair, 1, 1, 10)
        val signalStore = InMemorySignalProtocolStore(identityKeyPair, registrationId)

        private var currentPreKeyId = 1
        private val currentSignedPreKeyId = 1

        init {
            addPreKeysToStore(signalStore, preKeys)
        }

        override fun toString(): String {
            return "MockDevice(id=$id; registrationId=$registrationId)"
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

    private class MockUser(id: Long, initialDeviceCount: Int = 1) {
        val userId = UserId(id)
        val identityKeyPair = generateKeyPair()

        var devices = (0..initialDeviceCount - 1).map { createDevice(it) }.toMutableList()
        val deviceIds: List<Int>
            get() = devices.map { it.id }

        /** Shortcut for accessing the first device's SignalProtocolStore. */
        val signalStore: SignalProtocolStore
            get() = devices[0].signalStore

        val firstDevice: MockDevice = devices[0]

        fun getDevice(deviceId: Int): MockDevice = devices[deviceId - initialDeviceId]
        private fun setDevice(deviceId: Int, device: MockDevice) {
            devices[deviceId - initialDeviceId] = device
        }

        fun newDevice(): MockDevice {
            val device = createDevice(devices.size)
            devices.add(device)
            return device
        }

        private fun createDevice(deviceId: Int): MockDevice {
            val newDevice = MockDevice(identityKeyPair, deviceId + initialDeviceId)
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

    private val connectionTag = 0

    private val eventLogService = MockEventLogService()

    private fun <T> flatten(vararg lists: List<T>): List<T> {
        val r = ArrayList<T>()

        lists.forEach { r.addAll(it) }

        return r
    }

    private fun createPreKeyClientMock(target: MockUser, expectedDeviceIds: List<Int>): PreKeyClient {
        val client = mock<PreKeyClient>()

        val request = PreKeyRetrievalRequest(target.userId, expectedDeviceIds)
        val response = PreKeyRetrievalResponse(null, target.getBundles(expectedDeviceIds))

        whenever(client.retrieve(any(), eq(request))).thenReturn(response)

        return client
    }

    private fun createCipherService(client: PreKeyClient, user: MockUser): MessageCipherServiceImpl {
        val authTokenManager = MockAuthTokenManager()
        return MessageCipherServiceImpl(user.userId, authTokenManager, client, user.signalStore, eventLogService)
    }

    private fun assertSessionStatus(signalProtocolStore: SignalProtocolStore, target: MockUser, expectedDeviceIds: List<Int>, exists: Boolean) {
        expectedDeviceIds.forEach { deviceId ->
            val address = SlyAddress(target.userId, deviceId)
            val actual = signalProtocolStore.containsSession(address.toSignalAddress())

            if (exists)
                assertTrue(actual, "Session doesn't exist for deviceId=$deviceId")
            else
                assertFalse(actual, "Session exists for deviceId=$deviceId")
        }
    }

    private fun assertSessionsExist(user: MockUser, target: MockUser, expectedDeviceIds: List<Int>) {
        assertSessionStatus(user.signalStore, target, expectedDeviceIds, true)
    }

    private fun assertSessionsNotExist(user: MockUser, target: MockUser, expectedDeviceIds: List<Int>) {
        assertSessionStatus(user.signalStore, target, expectedDeviceIds, false)
    }

    private fun assertSessionsMatch(user: MockUser, target: MockUser, expectedDeviceIds: List<Int>) {
        expectedDeviceIds.forEach { deviceId ->
            val address = SlyAddress(target.userId, deviceId)
            val sessionCipher = SessionCipher(user.signalStore, address.toSignalAddress())

            val device = target.getDevice(deviceId)

            assertEquals(device.registrationId, sessionCipher.remoteRegistrationId, "Registration IDs for deviceId=$deviceId don't match")
        }
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

        //shouldn't throw
        p.get()
    }

    @Test
    fun `it should not fetch remote key data during encryption when a user has an existing session`() {
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

    @Test
    fun `it should not fetch remote key data during encryption if the sender is yourself and no sessions exist`() {
        val user = MockUser(1)

        val client = mock<PreKeyClient>()

        val cipherService = createCipherService(client, user)

        val p = cipherService.encrypt(user.userId, randomSerializedMessage(), connectionTag)

        cipherService.processQueue(false)

        val result = p.get()
        assertTrue(result.encryptedMessages.isEmpty(), "Encrypted message list not empty")

        verify(client, never()).retrieve(any(), any())
    }

    //tests PreKeyWhisper messages
    @Test
    fun `decryption should succeed when the receiver has no existing session`() {
        val sender = MockUser(1)
        val receiver = MockUser(2)

        sender.addSessions(receiver, receiver.deviceIds)

        val originalMessage = randomSerializedMessage()

        //first we create the message to send
        val senderService = createCipherService(mock(), sender)

        val encryptionResult = senderService.encrypt(receiver.userId, originalMessage, connectionTag)
        senderService.processQueue(false)
        val encryptedMessage = encryptionResult.get().encryptedMessages[0]

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
        val decryptedMessage = decryptionResult.get().data

        assertThat(decryptedMessage).isEqualTo(originalMessage).`as`("Decrypted should match original")
    }

    //XXX if you actually delete a session on the receiver side, and the sender still believes it exists and sends
    //a message, you get a NPE during decryption
    //however in normal usage this can't occur

    private fun testAddDevice(body: (user: MockUser, target: MockUser, List<MockDevice>) -> Unit) {
        val user = MockUser(1)

        val target = MockUser(2, 3)

        val missing = listOf(3)
        val info = DeviceMismatchContent(emptyList(), missing, emptyList())

        val client = createPreKeyClientMock(target, missing)

        val cipherService = createCipherService(client, user)

        val p = cipherService.updateDevices(target.userId, info)

        cipherService.processQueue(false)

        p.get()

        body(user, target, missing.map { target.getDevice(it) })
    }

    private fun testRemoveDevice(body: (user: MockUser, target: MockUser, List<MockDevice>) -> Unit) {
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

        body(user, target, removed.map { target.getDevice(it) })
    }

    private fun testReplaceDevice(body: (user: MockUser, target: MockUser, List<MockDevice>) -> Unit) {
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

        body(user, target, stale.map { target.getDevice(it) })
    }

    @Test
    fun `given device info, it should add new device sessions`() {
        testAddDevice { user, target, missing ->
            assertSessionsMatch(user, target, missing.map { it.id })
        }
    }

    @Test
    fun `given device info, it should delete removed device sessions`() {
        testRemoveDevice { user, target, removed ->
            assertSessionsNotExist(user, target, removed.map { it.id })
        }
    }

    @Test
    fun `given device info, it should refresh stale devices`() {
        testReplaceDevice { user, target, stale ->
            assertSessionsMatch(user, target, stale.map { it.id })
        }
    }

    private fun testAddSelfDevice(body: (self: MockUser, List<DeviceInfo>) -> Unit) {
        val self = MockUser(1)

        val otherDevice = self.newDevice()
        val otherDeviceIds = listOf(otherDevice.id)
        val otherDevices = listOf(
            DeviceInfo(otherDevice.id, otherDevice.registrationId)
        )

        val client = createPreKeyClientMock(self, otherDeviceIds)

        val cipherService = createCipherService(client, self)

        val p = cipherService.updateSelfDevices(otherDevices)

        cipherService.processQueue(false)

        p.get()

        body(self, otherDevices)
    }

    @Test
    fun `it should add missing self devices to the given list when updateSelfDevices is called`() {
        testAddSelfDevice { self, otherDevices ->
            assertSessionsExist(self, self, otherDevices.map { it.id })
        }
    }

    @Test
    fun `it should remove missing devices from the given list when updateSelfDevices is called`() {
        val self = MockUser(1)

        val otherDevice = self.newDevice()
        val otherDeviceIds = listOf(otherDevice.id)
        val otherDevices = emptyList<DeviceInfo>()

        val client = createPreKeyClientMock(self, emptyList())

        val cipherService = createCipherService(client, self)

        val p = cipherService.updateSelfDevices(otherDevices)

        cipherService.processQueue(false)

        p.get()

        assertSessionsNotExist(self, self, otherDeviceIds)
    }

    @Test
    fun `it should update stale entries from the given list when updateSelfDevices is called`() {
        val self = MockUser(1)

        val otherDevice = self.newDevice()
        val otherDeviceIds = listOf(otherDevice.id)
        self.addSessions(self, listOf(otherDevice.id))

        val newOtherDevice = self.swapDevice(otherDevice.id)

        val otherDevices = listOf(
            DeviceInfo(newOtherDevice.id, newOtherDevice.registrationId)
        )

        val client = createPreKeyClientMock(self, otherDeviceIds)

        val cipherService = createCipherService(client, self)

        val p = cipherService.updateSelfDevices(otherDevices)

        cipherService.processQueue(false)

        p.get()

        assertSessionsMatch(self, self, otherDeviceIds)
    }

    @Test
    fun `it should add a new self device when addSelfDevice is called`() {
        val self = MockUser(1)

        val otherDevice = self.newDevice()
        val otherDeviceIds = listOf(otherDevice.id)

        val client = createPreKeyClientMock(self, otherDeviceIds)

        val cipherService = createCipherService(client, self)

        val p = cipherService.addSelfDevice(DeviceInfo(otherDevice.id, otherDevice.registrationId))

        cipherService.processQueue(false)

        p.get()

        assertSessionsExist(self, self, otherDeviceIds)
    }

    //TODO
    //self device uses the same stuff under the hood, although we should have tests for that as well
    //same for fetching during a new encryption

    @Test
    fun `it should log session creation for new devices`() {
        val currentTime = 1L

        withTimeAs(currentTime) {
            testAddDevice { user, target, missing ->
                val events = missing.map {
                    val data = SecurityEventData.SessionCreated(SlyAddress(target.userId, it.id), it.registrationId)

                    LogEvent.Security(
                        LogTarget.Conversation(target.userId),
                        currentTime,
                        data
                    )
                }

                assertThat(eventLogService.loggedEvents).apply {
                    `as`("Should log session creation")
                    containsAll(events)
                }
            }
        }
    }

    @Test
    fun `it should log session deletion for removed devices`() {
        val currentTime = 1L

        withTimeAs(currentTime) {
            testRemoveDevice { user, target, removed ->
                val events = removed.map {
                    val data = SecurityEventData.SessionRemoved(SlyAddress(target.userId, it.id))

                    LogEvent.Security(
                        LogTarget.Conversation(target.userId),
                        currentTime,
                        data
                    )
                }

                assertThat(eventLogService.loggedEvents).apply {
                    `as`("Should log session deletion")
                    containsAll(events)
                }
            }
        }
    }

    @Test
    fun `it should log session creation and deletion on stale devices`() {
        val currentTime = 1L

        withTimeAs(currentTime) {
            testReplaceDevice { user, target, stale ->
                val events = ArrayList<LogEvent.Security>()

                stale.forEach {
                    val removedData = SecurityEventData.SessionRemoved(SlyAddress(target.userId, it.id))
                    val addedData = SecurityEventData.SessionCreated(SlyAddress(target.userId, it.id), it.registrationId)

                    events.add(LogEvent.Security(
                        LogTarget.Conversation(target.userId),
                        currentTime,
                        removedData
                    ))

                    events.add(LogEvent.Security(
                        LogTarget.Conversation(target.userId),
                        currentTime,
                        addedData
                    ))
                }

                assertThat(eventLogService.loggedEvents).apply {
                    `as`("Should log both session deletion and creation")
                    containsAll(events)
                }
            }
        }
    }

    private fun testClearDevices(body: (self: MockUser, target: MockUser) -> Unit) {
        val self = MockUser(1)

        val target = MockUser(2, 2)
        self.addSessions(target, target.deviceIds)

        val cipherService = createCipherService(mock(), self)

        val p = cipherService.clearDevices(target.userId)

        cipherService.processQueue(false)

        p.get()

        body(self, target)
    }

    @Test
    fun `it should log device clears when clearDevices is called`() {
        val currentTime = 1L

        withTimeAs(currentTime) {
            testClearDevices { self, target ->
                val devices = target.deviceIds
                val events = ArrayList<LogEvent.Security>()

                devices.forEach {
                    val addr = SlyAddress(target.userId, it)

                    events.add(LogEvent.Security(
                        LogTarget.Conversation(target.userId),
                        currentTime,
                        SecurityEventData.SessionRemoved(addr)
                    ))
                }

                assertThat(eventLogService.loggedEvents).apply {
                    `as`("Should log session deletetion events")
                    containsAll(events)
                }
            }
        }
    }

    @Test
    fun `it should remove all devices for a user when clearDevices is called`() {
        testClearDevices { self, target ->
            assertSessionsNotExist(self, target, emptyList())
        }
    }

    private fun doDiff(current: List<Int>, received: List<DeviceInfo>, devices: Map<Int, Int>): DeviceMismatchContent {
        return deviceDiff(current, received) { devices.getOrElse(it, { 0 }) }
    }

    @Test
    fun `deviceDiff should return missing device entries`() {
        val devices = mapOf(1 to 1)

        val current = listOf(1)
        val received = listOf(
            DeviceInfo(1, 1),
            DeviceInfo(2, 2)
        )

        val diff = doDiff(current, received, devices)

        val expected = DeviceMismatchContent(emptyList(), listOf(2), emptyList())

        assertEquals(expected, diff, "Invalid diff")
    }

    @Test
    fun `deviceDiff should return removed device entries`() {
        val devices = mapOf(1 to 1)

        val current = listOf(1)
        val received = emptyList<DeviceInfo>()

        val diff = doDiff(current, received, devices)

        val expected = DeviceMismatchContent(emptyList(), emptyList(), listOf(1))

        assertEquals(expected, diff, "Invalid diff")
    }

    @Test
    fun `deviceDiff should return stale device entries`() {
        val devices = mapOf(1 to 1)

        val current = listOf(1)
        val received = listOf(
            DeviceInfo(1, 2)
        )

        val diff = doDiff(current, received, devices)

        val expected = DeviceMismatchContent(listOf(1), emptyList(), emptyList())

        assertEquals(expected, diff, "Invalid diff")
    }
}