package com.vfpowertech.keytap.services.crypto

import com.fasterxml.jackson.databind.ObjectMapper
import com.vfpowertech.keytap.core.BuildConfig
import com.vfpowertech.keytap.core.KeyTapAddress
import com.vfpowertech.keytap.core.UserId
import com.vfpowertech.keytap.core.crypto.hexify
import com.vfpowertech.keytap.core.crypto.unhexify
import com.vfpowertech.keytap.core.http.JavaHttpClient
import com.vfpowertech.keytap.core.http.api.prekeys.*
import com.vfpowertech.keytap.services.*
import org.whispersystems.libsignal.IdentityKey
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
)

fun SerializedPreKeySet.toPreKeyBundle(): PreKeyBundle {
    val objectMapper = ObjectMapper()
    val registrationId = 0
    val deviceId = 1
    val oneTimePreKey = objectMapper.readValue(preKey, UnsignedPreKeyPublicData::class.java)
    val signedPreKey = objectMapper.readValue(signedPreKey, SignedPreKeyPublicData::class.java)
    return PreKeyBundle(
        registrationId,
        deviceId,
        oneTimePreKey.id,
        oneTimePreKey.getECPublicKey(),
        signedPreKey.id,
        signedPreKey.getECPublicKey(),
        signedPreKey.signature,
        IdentityKey(publicKey.unhexify(), 0)
    )
}

private interface CipherWork
private data class EncryptionWork(val userId: UserId, val message: String, val connectionTag: Int) : CipherWork
private data class DecryptionWork(val userId: UserId, val encryptedMessages: List<EncryptedMessageV0>) : CipherWork
private data class OfflineDecryptionWork(val encryptedMessages: Map<UserId, List<EncryptedMessageV0>>) : CipherWork
private class NoMoreWork : CipherWork

class MessageCipherService(
    private val userLoginData: UserLoginData,
    //the store is only ever used in the work thread, so no locking is done
    private val signalStore: SignalProtocolStore,
    private val serverUrls: BuildConfig.ServerUrls
) : Runnable {
    private var thread: Thread? = null

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

    fun decryptOffline(encryptedMessages: Map<UserId, List<EncryptedMessageV0>>) {
       workQueue.add(OfflineDecryptionWork(encryptedMessages))
    }

    fun decrypt(userId: UserId, messages: List<EncryptedMessageV0>) {
        workQueue.add(DecryptionWork(userId,  messages))
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
            val sessionCipher = getSessionCipher(userId)

            val encrypted = sessionCipher.encrypt(message.toByteArray(Charsets.UTF_8))

            val isPreKey = when (encrypted) {
                is PreKeySignalMessage -> true
                is SignalMessage -> false
                else -> throw RuntimeException("Invalid message type: ${encrypted.javaClass.name}")
            }

            val m = EncryptedMessageV0(isPreKey, encrypted.serialize().hexify())
            EncryptionOk(m, work.connectionTag)
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

    private fun decryptMessagesForUser(userId: UserId, encryptedMessages: List<EncryptedMessageV0>): MessageListDecryptionResult {
        val failed = ArrayList<DecryptionFailure>()
        val succeeded = ArrayList<String>()

        val address = KeyTapAddress(userId, 0).toSignalAddress()
        val sessionCipher = SessionCipher(signalStore, address)

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
        val result = work.encryptedMessages.mapValues { entry ->
            decryptMessagesForUser(entry.key, entry.value)
        }

        offlineSubject.onNext(OfflineDecryptionResult(result))
    }

    private fun handleDecryption(work: DecryptionWork) {
        val result = decryptMessagesForUser(work.userId, work.encryptedMessages)
        decryptionSubject.onNext(DecryptionResult(work.userId, result))
    }

    private fun fetchPreKeyBundle(userId: UserId): PreKeyBundle {
        //FIXME
        val authToken = userLoginData.authToken ?: throw NoAuthTokenException()
        val request = PreKeyRetrievalRequest(authToken, userId)
        val response = PreKeyRetrievalClient(serverUrls.API_SERVER, JavaHttpClient()).retrieve(request)

        return if (!response.isSuccess)
            throw RuntimeException(response.errorMessage)
        else {
            response.keyData ?: throw RuntimeException("No key data for $userId")
            response.keyData.toPreKeyBundle()
        }
    }

    private fun getSessionCipher(userId: UserId): SessionCipher {
        val address = KeyTapAddress(userId, 0).toSignalAddress()
        val containsSession = signalStore.containsSession(address)

        return if (containsSession)
            SessionCipher(signalStore, address)
        else {
            val bundle = fetchPreKeyBundle(userId)
            val builder = SessionBuilder(signalStore, address)
            builder.process(bundle)
            SessionCipher(signalStore, address)
        }
    }
}