package io.slychat.messenger.services.crypto

import io.slychat.messenger.core.SlyAddress
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.http.api.authentication.DeviceInfo
import io.slychat.messenger.core.http.api.prekeys.PreKeyClient
import io.slychat.messenger.core.http.api.prekeys.PreKeyRetrievalRequest
import io.slychat.messenger.core.http.api.prekeys.toPreKeyBundle
import io.slychat.messenger.core.mapToSet
import io.slychat.messenger.core.relay.base.DeviceMismatchContent
import io.slychat.messenger.services.auth.AuthTokenManager
import io.slychat.messenger.services.messaging.EncryptedMessageInfo
import io.slychat.messenger.services.messaging.EncryptionResult
import nl.komponents.kovenant.Deferred
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import org.slf4j.LoggerFactory
import org.whispersystems.libsignal.SessionBuilder
import org.whispersystems.libsignal.SessionCipher
import org.whispersystems.libsignal.protocol.PreKeySignalMessage
import org.whispersystems.libsignal.protocol.SignalMessage
import org.whispersystems.libsignal.state.PreKeyBundle
import org.whispersystems.libsignal.state.SignalProtocolStore
import java.util.*
import java.util.concurrent.ArrayBlockingQueue

class MessageCipherServiceImpl(
    private val selfId: UserId,
    private val authTokenManager: AuthTokenManager,
    private val preKeyClient: PreKeyClient,
    //the store is only ever used in the work thread, so no locking is done
    private val signalStore: SignalProtocolStore
) : MessageCipherService, Runnable {
    companion object {
        fun deviceDiff(currentDeviceIds: List<Int>, receivedDevices: List<DeviceInfo>, getRegistrationId: (Int) -> Int): DeviceMismatchContent {
            val missing = ArrayList<Int>()
            val stale = ArrayList<Int>()

            val receivedDeviceIds = receivedDevices.mapToSet { it.id }

            //need: in common
            //not in currentDeviceIds
            //no longer in currentDeviceIds (ie not in receivedDeviceIds)
            val removedIds = currentDeviceIds - receivedDeviceIds

            receivedDevices.forEach {
                if (it.id !in removedIds) {
                    val remoteRegistrationId = getRegistrationId(it.id)

                    if (remoteRegistrationId == 0)
                        missing.add(it.id)
                    else if (remoteRegistrationId != it.registrationId)
                        stale.add(it.id)
                    //else we already have the device, do nothing
                }
            }

            return DeviceMismatchContent(stale, missing, removedIds.toList())
        }
    }
    private sealed class CipherWork {
        class Encryption(
            val userId: UserId,
            val message: ByteArray,
            val connectionTag: Int,
            val deferred: Deferred<EncryptionResult, Exception>
        ) : CipherWork()

        class Decryption(
            val address: SlyAddress,
            val encryptedMessage: EncryptedMessageInfo,
            val deferred: Deferred<DecryptionResult, Exception>
        ) : CipherWork()

        class UpdateDevices(
            val userId: UserId,
            val info: DeviceMismatchContent,
            val deferred: Deferred<Unit, Exception>
        ) : CipherWork()

        class UpdateSelfDevices(
            val otherDevices: List<DeviceInfo>,
            val deferred: Deferred<Unit, Exception>
        ) : CipherWork()

        class AddSelfDevice(
            val deviceInfo: DeviceInfo,
            val deferred: Deferred<Unit, Exception>
        ) : CipherWork()

        class NoMoreWork : CipherWork()
    }

    private var thread: Thread? = null

    private val log = LoggerFactory.getLogger(javaClass)

    private val workQueue = ArrayBlockingQueue<CipherWork>(20)

    override fun start() {
        if (thread != null)
            return

        val th = Thread(this)
        th.isDaemon = true
        th.start()

        thread = th
    }

    override fun shutdown(join: Boolean) {
        val th = thread ?: return

        workQueue.add(CipherWork.NoMoreWork())
        if (join)
            th.join()
        thread = null
    }

    override fun encrypt(userId: UserId, message: ByteArray, connectionTag: Int): Promise<EncryptionResult, Exception> {
        val d = deferred<EncryptionResult, Exception>()
        workQueue.add(CipherWork.Encryption(userId, message, connectionTag, d))
        return d.promise
    }

    override fun decrypt(address: SlyAddress, messages: EncryptedMessageInfo): Promise<DecryptionResult, Exception> {
        val d = deferred<DecryptionResult, Exception>()
        workQueue.add(CipherWork.Decryption(address, messages, d))
        return d.promise
    }

    override fun updateDevices(userId: UserId, info: DeviceMismatchContent): Promise<Unit, Exception> {
        val d = deferred<Unit, Exception>()
        workQueue.add(CipherWork.UpdateDevices(userId, info, d))
        return d.promise
    }

    override fun updateSelfDevices(otherDevices: List<DeviceInfo>): Promise<Unit, Exception> {
        val d = deferred<Unit, Exception>()
        workQueue.add(CipherWork.UpdateSelfDevices(otherDevices, d))
        return d.promise
    }

    override fun addSelfDevice(deviceInfo: DeviceInfo): Promise<Unit, Exception> {
        val d = deferred<Unit, Exception>()
        workQueue.add(CipherWork.AddSelfDevice(deviceInfo, d))
        return d.promise
    }

    override fun run() {
        processQueue(true)
    }

    //XXX used for testing only
    internal fun processQueue(block: Boolean) {
        while (true) {
            val work = if (block) {
                workQueue.take() ?: continue
            }
            else {
                workQueue.poll()
            }

            if (work == null)
                return

            if (!processWork(work))
                break
        }
    }

    /** Returns true to continue, false to exit. */
    private fun processWork(work: CipherWork): Boolean {
        when (work) {
            is CipherWork.Encryption -> handleEncryption(work)
            is CipherWork.Decryption -> handleDecryption(work)
            is CipherWork.UpdateDevices -> handleDeviceUpdate(work)
            is CipherWork.UpdateSelfDevices -> handleUpdateSelfDevices(work)
            is CipherWork.AddSelfDevice -> handleAddSelfDevice(work)
            is CipherWork.NoMoreWork -> return false
            else -> {
                log.error("Unknown work type: {}", work)
            }
        }

        return true
    }

    private fun handleAddSelfDevice(work: CipherWork.AddSelfDevice) {
        val diff = DeviceMismatchContent(emptyList(), listOf(work.deviceInfo.id), emptyList())

        applyDiff(diff, selfId, work.deferred)
    }

    private fun handleUpdateSelfDevices(work: CipherWork.UpdateSelfDevices) {
        val currentDeviceIds = signalStore.getSubDeviceSessions(selfId.toString())

        val diff = deviceDiff(currentDeviceIds, work.otherDevices) {
            val address = SlyAddress(selfId, it)
            val sessionCipher = SessionCipher(signalStore, address.toSignalAddress())
            sessionCipher.remoteRegistrationId
        }

        applyDiff(diff, selfId, work.deferred)
    }

    private fun handleDeviceUpdate(work: CipherWork.UpdateDevices) {
        val userId = work.userId

        val info = work.info

        applyDiff(info, userId, work.deferred)
    }

    private fun applyDiff(info: DeviceMismatchContent, userId: UserId, deferred: Deferred<Unit, Exception>) {
        try {
            val toRemove = HashSet(info.removed)
            toRemove.addAll(info.stale)

            toRemove.forEach { deviceId ->
                val address = SlyAddress(userId, deviceId)
                signalStore.deleteSession(address.toSignalAddress())
            }

            //remove any stale entries
            info.stale.map { deviceId ->
                val addr = SlyAddress(userId, deviceId)
                signalStore.deleteSession(addr.toSignalAddress())
            }

            val toAdd = HashSet(info.missing)
            toAdd.addAll(info.stale)

            if (toAdd.isNotEmpty())
                addNewBundles(userId, toAdd)

            deferred.resolve(Unit)
        }
        catch (e: Exception) {
            deferred.reject(e)
        }
    }

    private fun handleEncryption(work: CipherWork.Encryption) {
        val userId = work.userId
        val message = work.message
        try {
            val sessionCiphers = getSessionCiphers(userId)

            val messages = sessionCiphers.map {
                val deviceId = it.first
                val sessionCipher = it.second
                val encrypted = sessionCipher.encrypt(message)

                val isPreKey = when (encrypted) {
                    is PreKeySignalMessage -> true
                    is SignalMessage -> false
                    else -> throw RuntimeException("Invalid message type: ${encrypted.javaClass.name}")
                }

                val m = EncryptedPackagePayloadV0(isPreKey, encrypted.serialize())
                MessageData(deviceId, sessionCipher.remoteRegistrationId, m)
            }

            work.deferred.resolve(EncryptionResult(messages, work.connectionTag))
        }
        catch (e: Exception) {
            work.deferred.reject(e)
        }
    }

    private fun decryptEncryptedMessage(sessionCipher: SessionCipher, encryptedPackagePayload: EncryptedPackagePayloadV0): ByteArray {
        val payload = encryptedPackagePayload.payload

        val messageData = if (encryptedPackagePayload.isPreKeyWhisper)
            sessionCipher.decrypt(PreKeySignalMessage(payload))
        else
            sessionCipher.decrypt(SignalMessage(payload))

        return messageData
    }

    private fun handleDecryption(work: CipherWork.Decryption) {
        val sessionCipher = SessionCipher(signalStore, work.address.toSignalAddress())
        val messageId = work.encryptedMessage.messageId

        try {
            val message = decryptEncryptedMessage(sessionCipher, work.encryptedMessage.payload)
            work.deferred.resolve(
                DecryptionResult(messageId, message)
            )
        }
        catch (e: Exception) {
            work.deferred.reject(e)
        }
    }

    private fun fetchPreKeyBundles(userId: UserId, deviceIds: Collection<Int>): List<PreKeyBundle> {
        val response = authTokenManager.map { userCredentials ->
            val request = PreKeyRetrievalRequest(userId, deviceIds.toList())
            preKeyClient.retrieve(userCredentials, request)
        }.get()

        if (!response.isSuccess)
            throw RuntimeException(response.errorMessage)
        else {
            if (response.bundles.isEmpty())
                throw RuntimeException("No key data for $userId")

            val bundles = ArrayList<PreKeyBundle>()

            for ((deviceId, bundle) in response.bundles) {
                if (bundle == null) {
                    log.warn("No key data available for {}:{}", userId.long, deviceId)
                    continue
                }

                bundles.add(bundle.toPreKeyBundle(deviceId))
            }

            return bundles
        }
    }

    private fun processPreKeyBundles(userId: UserId, bundles: Collection<PreKeyBundle>): List<Pair<Int, SessionCipher>> {
        return bundles.map { bundle ->
            val address = SlyAddress(userId, bundle.deviceId).toSignalAddress()
            val builder = SessionBuilder(signalStore, address)
            //this can fail with an InvalidKeyException if the signed key signature doesn't match
            builder.process(bundle)
            bundle.deviceId to SessionCipher(signalStore, address)
        }
    }

    /** Fetches and processes prekey bundles for the given user and device IDs. */
    private fun addNewBundles(userId: UserId, deviceIds: Collection<Int>): List<Pair<Int, SessionCipher>> {
        val bundles = fetchPreKeyBundles(userId, deviceIds)
        return processPreKeyBundles(userId, bundles)
    }

    private fun getSessionCiphers(userId: UserId): List<Pair<Int, SessionCipher>> {
        val devices = signalStore.getSubDeviceSessions(userId.long.toString())

        //check if we have any listed devices; if not, then fetch prekeys
        //else send what we have to relay and it'll tell us what to fix
        return if (devices.isNotEmpty()) {
            devices.map { deviceId ->
                val address = SlyAddress(userId, deviceId).toSignalAddress()
                deviceId to SessionCipher(signalStore, address)
            }
        }
        else {
            addNewBundles(userId, emptyList())
        }
    }
}