package io.slychat.messenger.services

import io.slychat.messenger.core.crypto.LAST_RESORT_PREKEY_ID
import io.slychat.messenger.core.crypto.generateLastResortPreKey
import io.slychat.messenger.core.crypto.generatePrekeys
import io.slychat.messenger.core.crypto.signal.GeneratedPreKeys
import io.slychat.messenger.core.http.api.prekeys.PreKeyAsyncClient
import io.slychat.messenger.core.http.api.prekeys.preKeyStorageRequestFromGeneratedPreKeys
import io.slychat.messenger.core.persistence.PreKeyPersistenceManager
import io.slychat.messenger.services.auth.AuthTokenManager
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.bind
import nl.komponents.kovenant.functional.map
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory
import org.whispersystems.libsignal.state.PreKeyRecord
import rx.Observable
import rx.Subscription

//TODO right now this only regens new keys on online
//should also check after processing a prekey from a received message
class PreKeyManagerImpl(
    networkAvailable: Observable<Boolean>,
    private val registrationId: Int,
    private val userLoginData: UserData,
    private val preKeyAsyncClient: PreKeyAsyncClient,
    private val preKeyPersistenceManager: PreKeyPersistenceManager,
    private val authTokenManager: AuthTokenManager
) : PreKeyManager {
    private val log = LoggerFactory.getLogger(javaClass)

    private var scheduledKeyCount = 0

    private var running = false

    private var isOnline = false

    private val networkAvailableSubscription: Subscription

    init {
        networkAvailableSubscription = networkAvailable.subscribe { status ->
            isOnline = status
            if (status && scheduledKeyCount > 0)
                scheduleUpload(scheduledKeyCount)
        }
    }

    private fun generate(count: Int): Promise<Pair<GeneratedPreKeys, PreKeyRecord>, Exception> {
        val keyVault = userLoginData.keyVault

        return preKeyPersistenceManager.getNextPreKeyIds() bind { preKeyIds ->
            val generatedPreKeys = generatePrekeys(keyVault.identityKeyPair, preKeyIds.nextSignedId, preKeyIds.nextUnsignedId, count)
            preKeyPersistenceManager.putGeneratedPreKeys(generatedPreKeys) map { generatedPreKeys }
        } bindUi { generatedPreKeys ->
            getLastResortPreKey(preKeyPersistenceManager) map { lastResortPreKey ->
                generatedPreKeys to lastResortPreKey
            }
        }
    }

    //assumes the keys are generated in a range
    private fun removeGeneratedKeys(generatedPreKeys: GeneratedPreKeys): Promise<Unit, Exception> {
        if (generatedPreKeys.oneTimePreKeys.isEmpty())
            return Promise.ofSuccess(Unit)

        val start = generatedPreKeys.oneTimePreKeys.first().id
        val end = start + generatedPreKeys.oneTimePreKeys.size

        return preKeyPersistenceManager.removeUnsignedPreKeyRange(start, end) bind {
            preKeyPersistenceManager.removeSignedPreKey(generatedPreKeys.signedPreKey.id)
        }
    }

    override fun checkForUpload() {
        authTokenManager.bind { userCredentials ->
            preKeyAsyncClient.getInfo(userCredentials) mapUi { response ->
                log.debug("Remaining prekeys: {}, requested to upload {}", response.remaining, response.uploadCount)
                scheduleUpload(response.uploadCount)
            }
        }
    }

    override fun scheduleUpload(keyRegenCount: Int) {
        if (!isOnline) {
            scheduledKeyCount = keyRegenCount
            return
        }

        if (running || keyRegenCount <= 0)
            return

        log.info("Requested to generate {} new prekeys", keyRegenCount)

        val keyVault = userLoginData.keyVault

        scheduledKeyCount = 0
        running = true

        //TODO need to mark whether or not a range has been pushed to the server or not
        //if the push fails, we should delete the batch?
        //TODO nfi what to do if server response fails
        authTokenManager.bind { userCredentials ->
            generate(keyRegenCount) bind { r ->
                val (generatedPreKeys, lastResortPreKey) = r
                val request = preKeyStorageRequestFromGeneratedPreKeys(registrationId, keyVault, generatedPreKeys, lastResortPreKey)
                preKeyAsyncClient.store(userCredentials, request)
            }
        } successUi { response ->
            running = false

            if (!response.isSuccess)
                log.error("PreKey push failed: {}", response.errorMessage)
            else
                log.info("Pushed prekeys to server")
        } failUi { e ->
            running = false

            log.error("PreKey push failed: {}", e.message, e)
        }
    }

    /** Get last resort prekey, or generate it if not yet created. */
    private fun getLastResortPreKey(preKeyPersistenceManager: PreKeyPersistenceManager): Promise<PreKeyRecord, Exception> {
        return preKeyPersistenceManager.getUnsignedPreKey(LAST_RESORT_PREKEY_ID) bind { maybeLastResortPreKey ->
            if (maybeLastResortPreKey != null)
                Promise.ofSuccess<PreKeyRecord, Exception>(maybeLastResortPreKey)
            else {
                val lastResortPreKey = generateLastResortPreKey()
                preKeyPersistenceManager.putLastResortPreKey(lastResortPreKey) map {
                    lastResortPreKey
                }
            }
        }
    }

    override fun shutdown() {
        networkAvailableSubscription.unsubscribe()
    }
}