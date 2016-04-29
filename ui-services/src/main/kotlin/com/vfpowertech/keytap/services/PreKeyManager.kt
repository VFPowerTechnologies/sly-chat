package com.vfpowertech.keytap.services

import com.vfpowertech.keytap.core.crypto.LAST_RESORT_PREKEY_ID
import com.vfpowertech.keytap.core.crypto.generateLastResortPreKey
import com.vfpowertech.keytap.core.crypto.generatePrekeys
import com.vfpowertech.keytap.core.crypto.signal.GeneratedPreKeys
import com.vfpowertech.keytap.core.persistence.PreKeyPersistenceManager
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.bind
import nl.komponents.kovenant.functional.map
import org.whispersystems.libsignal.state.PreKeyRecord

class PreKeyManager(
    private val userLoginData: UserLoginData,
    private val preKeyPersistenceManager: PreKeyPersistenceManager
) {
    fun generate(): Promise<Pair<GeneratedPreKeys, PreKeyRecord>, Exception> {
        val keyVault = userLoginData.keyVault
        val keyRegenCount = 30

        return preKeyPersistenceManager.getNextPreKeyIds() bind { preKeyIds ->
            val generatedPreKeys = generatePrekeys(keyVault.identityKeyPair, preKeyIds.nextSignedId, preKeyIds.nextUnsignedId, keyRegenCount)
            preKeyPersistenceManager.putGeneratedPreKeys(generatedPreKeys) map { generatedPreKeys }
        } bindUi { generatedPreKeys ->
            getLastResortPreKey(preKeyPersistenceManager) map { lastResortPreKey ->
                generatedPreKeys to lastResortPreKey
            }
        }
    }

    //assumes the keys are generated in a range
    fun removeGeneratedKeys(generatedPreKeys: GeneratedPreKeys): Promise<Unit, Exception> {
        if (generatedPreKeys.oneTimePreKeys.isEmpty())
            return Promise.ofSuccess(Unit)

        val start = generatedPreKeys.oneTimePreKeys.first().id
        val end = start + generatedPreKeys.oneTimePreKeys.size

        return preKeyPersistenceManager.removeUnsignedPreKeyRange(start, end) bind {
            preKeyPersistenceManager.removeSignedPreKey(generatedPreKeys.signedPreKey.id)
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