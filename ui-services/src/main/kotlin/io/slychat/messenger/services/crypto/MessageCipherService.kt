package io.slychat.messenger.services.crypto

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonPropertyOrder
import io.slychat.messenger.core.BuildConfig
import io.slychat.messenger.core.SlyAddress
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.http.api.prekeys.PreKeyClient
import io.slychat.messenger.core.http.api.prekeys.PreKeyRetrievalRequest
import io.slychat.messenger.core.http.api.prekeys.toPreKeyBundle
import io.slychat.messenger.services.*
import io.slychat.messenger.services.auth.AuthTokenManager
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

data class MessageDecryptionResult<T>(
    val messageId: String,
    val result: T
)

data class DecryptionFailure(val cause: Throwable)
data class MessageListDecryptionResult(
    val succeeded: List<MessageDecryptionResult<ByteArray>>,
    val failed: List<MessageDecryptionResult<DecryptionFailure>>
)

private interface CipherWork
private data class EncryptionWork(val userId: UserId, val message: ByteArray, val connectionTag: Int) : CipherWork
private data class DecryptionWork(val address: SlyAddress, val encryptedMessages: List<EncryptedMessageInfo>) : CipherWork
private class NoMoreWork : CipherWork

/** Represents a single message to a user. */
@JsonFormat(shape = JsonFormat.Shape.ARRAY)
@JsonPropertyOrder("deviceId", "registrationId", "payload")
data class MessageData(
    val deviceId: Int,
    val registrationId: Int,
    val payload: EncryptedPackagePayloadV0
)

class MessageCipherService(
    private val authTokenManager: AuthTokenManager,
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

    fun encrypt(userId: UserId, message: ByteArray, connectionTag: Int) {
        workQueue.add(EncryptionWork(userId, message, connectionTag))
    }

    fun decrypt(address: SlyAddress, messages: List<EncryptedMessageInfo>) {
        workQueue.add(DecryptionWork(address,  messages))
    }

    override fun run() {
        loop@ while (true) {
            val work = workQueue.take() ?: continue

            when (work) {
                is EncryptionWork -> handleEncryption(work)
                is DecryptionWork -> handleDecryption(work)
                is NoMoreWork -> break@loop
                else -> {
                    log.error("Unknown work type: {}", work)
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
                val encrypted = sessionCipher.encrypt(message)

                val isPreKey = when (encrypted) {
                    is PreKeySignalMessage -> true
                    is SignalMessage -> false
                    else -> throw RuntimeException("Invalid message type: ${encrypted.javaClass.name}")
                }

                val m = EncryptedPackagePayloadV0(isPreKey, encrypted.serialize())
                MessageData(deviceId, sessionCipher.remoteRegistrationId, m)
            }

            EncryptionOk(messages, work.connectionTag)
        }
        catch (e: Exception) {
            EncryptionUnknownFailure(e)
        }

        encryptionSubject.onNext(result)
    }

    private fun decryptEncryptedMessage(sessionCipher: SessionCipher, encryptedPackagePayload: EncryptedPackagePayloadV0): ByteArray {
        val payload = encryptedPackagePayload.payload

        val messageData = if (encryptedPackagePayload.isPreKeyWhisper)
            sessionCipher.decrypt(PreKeySignalMessage(payload))
        else
            sessionCipher.decrypt(SignalMessage(payload))

        return messageData
    }

    private fun decryptMessagesForUser(address: SlyAddress, encryptedMessages: List<EncryptedMessageInfo>): MessageListDecryptionResult {
        val failed = ArrayList<MessageDecryptionResult<DecryptionFailure>>()
        val succeeded = ArrayList<MessageDecryptionResult<ByteArray>>()

        val sessionCipher = SessionCipher(signalStore, address.toSignalAddress())

        encryptedMessages.forEach { encryptedMessageInfo ->
            try {
                val message = decryptEncryptedMessage(sessionCipher, encryptedMessageInfo.payload)
                val result = MessageDecryptionResult(encryptedMessageInfo.messageId, message)
                succeeded.add(result)
            }
            catch (e: Throwable) {
                val result = MessageDecryptionResult(encryptedMessageInfo.messageId, DecryptionFailure(e))
                failed.add(result)
            }
        }

        return MessageListDecryptionResult(succeeded, failed)
    }

    private fun handleDecryption(work: DecryptionWork) {
        val result = decryptMessagesForUser(work.address, work.encryptedMessages)
        decryptionSubject.onNext(DecryptionResult(work.address.id, result))
    }

    private fun fetchPreKeyBundles(userId: UserId): List<PreKeyBundle> {
        val response = authTokenManager.map { userCredentials ->
            val request = PreKeyRetrievalRequest(userId, listOf())
            PreKeyClient(serverUrls.API_SERVER, io.slychat.messenger.core.http.JavaHttpClient()).retrieve(userCredentials, request)
        }.get()

        if (!response.isSuccess)
            throw RuntimeException(response.errorMessage)
        else {
            if (response.bundles.isEmpty())
                throw RuntimeException("No key data for $userId")

            val bundles = ArrayList<PreKeyBundle>()

            for ((deviceId, bundle) in response.bundles) {
                if (bundle == null) {
                    log.error("No key data available for {}:{}", userId.long, deviceId)
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
                val address = SlyAddress(userId, deviceId).toSignalAddress()
                deviceId to SessionCipher(signalStore, address)
            }
        }
        else {
            val bundles = fetchPreKeyBundles(userId)

            bundles.map { bundle ->
                val address = SlyAddress(userId, bundle.deviceId).toSignalAddress()
                val builder = SessionBuilder(signalStore, address)
                //this can fail with an InvalidKeyException if the signed key signature doesn't match
                builder.process(bundle)
                bundle.deviceId to SessionCipher(signalStore, address)
            }
        }
    }
}