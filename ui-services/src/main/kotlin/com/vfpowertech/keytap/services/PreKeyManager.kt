package com.vfpowertech.keytap.services

import com.vfpowertech.keytap.core.crypto.LAST_RESORT_PREKEY_ID
import com.vfpowertech.keytap.core.crypto.generateLastResortPreKey
import com.vfpowertech.keytap.core.crypto.generatePrekeys
import com.vfpowertech.keytap.core.crypto.signal.GeneratedPreKeys
import com.vfpowertech.keytap.core.http.api.prekeys.PreKeyAsyncClient
import com.vfpowertech.keytap.core.http.api.prekeys.PreKeyInfoRequest
import com.vfpowertech.keytap.core.http.api.prekeys.preKeyStorageRequestFromGeneratedPreKeys
import com.vfpowertech.keytap.core.persistence.PreKeyPersistenceManager
import com.vfpowertech.keytap.services.auth.AuthTokenManager
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.bind
import nl.komponents.kovenant.functional.map
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory
import org.whispersystems.libsignal.state.PreKeyRecord

//TODO right now this only regens new keys on online
//should also check after processing a prekey from a received message
class PreKeyManager(
    private val application: KeyTapApplication,
    private val serverUrl: String,
    private val userLoginData: UserLoginData,
    private val preKeyPersistenceManager: PreKeyPersistenceManager,
    private val authTokenManager: AuthTokenManager
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private var scheduledKeyCount = 0

    private var running = false

    private var isOnline = false

    init {
        application.networkAvailable.subscribe { status ->
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

    fun checkForUpload() {
        authTokenManager.bind { authToken ->
            PreKeyAsyncClient(serverUrl).getInfo(PreKeyInfoRequest(authToken.string)) mapUi { response ->
                log.debug("Remaining prekeys: {}, requested to upload {}", response.remaining, response.uploadCount)
                scheduleUpload(response.uploadCount)
            }
        }
    }

    fun scheduleUpload(keyRegenCount: Int) {
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
        authTokenManager.bind { authToken ->
            generate(keyRegenCount) bind { r ->
                val (generatedPreKeys, lastResortPreKey) = r
                val request = preKeyStorageRequestFromGeneratedPreKeys(authToken.string, application.installationData.registrationId, keyVault, generatedPreKeys, lastResortPreKey)
                PreKeyAsyncClient(serverUrl).store(request)
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
}