@file:JvmName("PreKeyUtils")
package com.vfpowertech.keytap.core.http.api.prekeys

import com.vfpowertech.keytap.core.crypto.KeyVault
import com.vfpowertech.keytap.core.crypto.hexify
import com.vfpowertech.keytap.core.crypto.signal.GeneratedPreKeys
import com.vfpowertech.keytap.core.crypto.signal.UserPreKeySet
import com.vfpowertech.keytap.core.crypto.unhexify
import org.whispersystems.libsignal.IdentityKey
import org.whispersystems.libsignal.state.PreKeyRecord
import org.whispersystems.libsignal.state.SignedPreKeyRecord

fun serializePreKey(preKeyRecord: PreKeyRecord): String =
    preKeyRecord.serialize().hexify()

fun serializeOneTimePreKeys(oneTimePreKeys: List<PreKeyRecord>): List<String> =
    oneTimePreKeys.map { serializePreKey(it) }

fun serializeSignedPreKey(signedPreKeyRecord: SignedPreKeyRecord): String =
    signedPreKeyRecord.serialize().hexify()

fun preKeyStorageRequestFromGeneratedPreKeys(
    authToken: String,
    keyVault: KeyVault,
    generatedPreKeys: GeneratedPreKeys,
    lastResortPreKey: PreKeyRecord
): PreKeyStoreRequest {
    val identityKey = keyVault.identityKeyPair.publicKey.serialize().hexify()

    val signedPreKey = serializeSignedPreKey(generatedPreKeys.signedPreKey)
    val oneTimePreKeys = serializeOneTimePreKeys(generatedPreKeys.oneTimePreKeys)
    val serializedLastResortKey = serializePreKey(lastResortPreKey)

    return PreKeyStoreRequest(authToken, identityKey, signedPreKey, oneTimePreKeys, serializedLastResortKey)
}

fun userPreKeySetFromRetrieveResponse(response: PreKeyRetrievalResponse): UserPreKeySet? {
    val keyData = response.keyData ?: return null

    return UserPreKeySet(
        IdentityKey(keyData.identityKey.unhexify(), 0),
        SignedPreKeyRecord(keyData.signedPreKey.unhexify()),
        PreKeyRecord(keyData.preKey.unhexify())
    )
}
