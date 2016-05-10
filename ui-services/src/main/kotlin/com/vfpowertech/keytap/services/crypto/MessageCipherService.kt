package com.vfpowertech.keytap.services.crypto

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonPropertyOrder
import com.vfpowertech.keytap.core.BuildConfig
import com.vfpowertech.keytap.core.KeyTapAddress
import com.vfpowertech.keytap.core.UserId
import com.vfpowertech.keytap.core.crypto.hexify
import com.vfpowertech.keytap.core.crypto.unhexify
import com.vfpowertech.keytap.core.http.JavaHttpClient
import com.vfpowertech.keytap.core.http.api.prekeys.PreKeyClient
import com.vfpowertech.keytap.core.http.api.prekeys.PreKeyRetrievalRequest
import com.vfpowertech.keytap.core.http.api.prekeys.toPreKeyBundle
import com.vfpowertech.keytap.services.*
import com.vfpowertech.keytap.services.auth.AuthTokenManager
import org.slf4j.LoggerFactory
import org.whispersystems.libsignal.SessionBuilder
import org.whispersystems.libsignal.SessionCipher
import org.whispersystems.libsignal.protocol.PreKeySignalMessage
import org.whispersystems.libsignal.protocol.SignalMessage
import org.whispersystems.libsignal.state.PreKeyBundle
import org.whispersystems.libsignal.state.SignalProtocolStore
import rx.Observable
import rx.subjects.PublishSubject
import java.util.*
import java.util.concurrent.ArrayBlockingQueue

data class DecryptionFailure(val cause: Throwable)
data class MessageListDecryptionResult(
    val succeeded: List<String>,
    val failed: List<DecryptionFailure>
) {
    fun merge(other: MessageListDecryptionResult): MessageListDecryptionResult {
        val succeededMerged = ArrayList(succeeded)
        succeededMerged.addAll(other.succeeded)

        val failedMerged = ArrayList(failed)
        failedMerged.addAll(other.failed)

        return MessageListDecryptionResult(succeededMerged, failedMerged)
    }
}

private interface CipherWork
private data class EncryptionWork(val userId: UserId, val message: String, val connectionTag: Int) : CipherWork
private data class DecryptionWork(val address: KeyTapAddress, val encryptedMessages: List<EncryptedMessageV0>) : CipherWork
private data class OfflineDecryptionWork(val encryptedMessages: Map<KeyTapAddress, List<EncryptedMessageV0>>) : CipherWork
private class NoMoreWork : CipherWork

/** Represents a single message to a user. */
@JsonFormat(shape = JsonFormat.Shape.ARRAY)
@JsonPropertyOrder("deviceId", "registrationId", "payload")
data class MessageData(
    val deviceId: Int,
    val registrationId: Int,
    val payload: EncryptedMessageV0
)

class MessageCipherService(
    private val authTokenManager: AuthTokenManager,
    private val userLoginData: UserLoginData,
    //the store is only ever used in the work thread, so no locking is done
    private val signalStore: SignalProtocolStore,
    private val serverUrls: BuildConfig.ServerUrls
) : Runnable {
    private var thread: Thread? = null

    private val log = LoggerFactory.getLogger(javaClass)

    private val workQueue = ArrayBlockingQueue<CipherWork>(20)
    private val encryptionSubject = PublishSubject.create<EncryptionResult>()
    val encryptedMessages: Observable<EncryptionResult> = encryptionSubject

    private val decryptionSubject = PublishSubject.create<DecryptionResult>()
    val decryptedMessages: Observable<DecryptionResult> = decryptionSubject

    private val offlineSubject = PublishSubject.create<OfflineDecryptionResult>()
    val offlineDecryptedMessages = offlineSubject

    fun start() {
        if (thread != null)
            return

        val th = Thread(this)
        th.isDaemon = true
        th.start()

        thread = th
    }

    fun shutdown(join: Boolean = false) {
        val th = thread ?: return

        workQueue.add(NoMoreWork())
        if (join)
            th.join()
        thread = null
    }

    fun encrypt(userId: UserId, message: String, connectionTag: Int) {
        workQueue.add(EncryptionWork(userId, message, connectionTag))
    }

    fun decryptOffline(encryptedMessages: Map<KeyTapAddress, List<EncryptedMessageV0>>) {
       workQueue.add(OfflineDecryptionWork(encryptedMessages))
    }

    fun decrypt(address: KeyTapAddress, messages: List<EncryptedMessageV0>) {
        workQueue.add(DecryptionWork(address,  messages))
    }

    override fun run() {
        loop@ while (true) {
            val work = workQueue.poll() ?: continue

            when (work) {
                is EncryptionWork -> handleEncryption(work)
                is DecryptionWork -> handleDecryption(work)
                is OfflineDecryptionWork -> handleOfflineDecryption(work)
                is NoMoreWork -> break@loop
                else -> {
                    println("Unknown work type")
                }
            }
        }
    }

    private fun handleEncryption(work: EncryptionWork) {
        val userId = work.userId
        val message = work.message
        val result = try {
            val sessionCiphers = getSessionCiphers(userId)

            val messages = sessionCiphers.map {
                val deviceId = it.first
                val sessionCipher = it.second
                val encrypted = sessionCipher.encrypt(message.toByteArray(Charsets.UTF_8))

                val isPreKey = when (encrypted) {
                    is PreKeySignalMessage -> true
                    is SignalMessage -> false
                    else -> throw RuntimeException("Invalid message type: ${encrypted.javaClass.name}")
                }

                val m = EncryptedMessageV0(isPreKey, encrypted.serialize().hexify())
                MessageData(deviceId, sessionCipher.remoteRegistrationId, m)
            }

            EncryptionOk(messages, work.connectionTag)
        }
        catch (e: Exception) {
            EncryptionUnknownFailure(e)
        }

        encryptionSubject.onNext(result)
    }

    private fun decryptEncryptedMessage(sessionCipher: SessionCipher, encryptedMessage: EncryptedMessageV0): String {
        val payload = encryptedMessage.payload.unhexify()

        val messageData = if (encryptedMessage.isPreKeyWhisper)
            sessionCipher.decrypt(PreKeySignalMessage(payload))
        else
            sessionCipher.decrypt(SignalMessage(payload))

        return String(messageData, Charsets.UTF_8)
    }

    private fun decryptMessagesForUser(address: KeyTapAddress, encryptedMessages: List<EncryptedMessageV0>): MessageListDecryptionResult {
        val failed = ArrayList<DecryptionFailure>()
        val succeeded = ArrayList<String>()

        val sessionCipher = SessionCipher(signalStore, address.toSignalAddress())

        encryptedMessages.forEach { encryptedMessage ->
            try {
                val message = decryptEncryptedMessage(sessionCipher, encryptedMessage)
                succeeded.add(message)
            }
            catch (e: Throwable) {
                failed.add(DecryptionFailure(e))
            }
        }

        return MessageListDecryptionResult(succeeded, failed)
    }

    private fun handleOfflineDecryption(work: OfflineDecryptionWork) {
        //here we map from possible multiple KeyTapAddresses of the same user to the same user id
        val result = HashMap<UserId, MessageListDecryptionResult>()

        work.encryptedMessages.forEach { entry ->
            val address = entry.key
            val decrypted = decryptMessagesForUser(address, entry.value)

            val existing = result[address.id]
            if (existing == null)
                result[address.id] = decrypted
            else
                result[address.id] = existing.merge(decrypted)
        }

        offlineSubject.onNext(OfflineDecryptionResult(result))
    }

    private fun handleDecryption(work: DecryptionWork) {
        val result = decryptMessagesForUser(work.address, work.encryptedMessages)
        decryptionSubject.onNext(DecryptionResult(work.address.id, result))
    }

    private fun fetchPreKeyBundles(userId: UserId): List<PreKeyBundle> {
        val response = authTokenManager.map { userCredentials ->
            val request = PreKeyRetrievalRequest(userId, listOf())
            PreKeyClient(serverUrls.API_SERVER, JavaHttpClient()).retrieve(userCredentials, request)
        }.get()

        if (!response.isSuccess)
            throw RuntimeException(response.errorMessage)
        else {
            if (response.bundles.isEmpty())
                throw RuntimeException("No key data for $userId")

            val bundles = ArrayList<PreKeyBundle>()

            for ((deviceId, bundle) in response.bundles) {
                if (bundle == null) {
                    log.error("No key data available for {}:{}", userId, deviceId)
                    continue
                }

                bundles.add(bundle.toPreKeyBundle(deviceId))
            }

            return bundles
        }
    }

    private fun getSessionCiphers(userId: UserId): List<Pair<Int, SessionCipher>> {
        //FIXME
        val devices = signalStore.getSubDeviceSessions(userId.long.toString())

        //check if we have any listed devices; if not, then fetch prekeys
        //else send what we have to relay and it'll tell us what to fix
        return if (devices.isNotEmpty()) {
            devices.map { deviceId ->
                val address = KeyTapAddress(userId, deviceId).toSignalAddress()
                deviceId to SessionCipher(signalStore, address)
            }
        }
        else {
            val bundles = fetchPreKeyBundles(userId)

            bundles.map { bundle ->
                val address = KeyTapAddress(userId, bundle.deviceId).toSignalAddress()
                val builder = SessionBuilder(signalStore, address)
                //this can fail with an InvalidKeyException if the signed key signature doesn't match
                builder.process(bundle)
                bundle.deviceId to SessionCipher(signalStore, address)
            }
        }
    }
}