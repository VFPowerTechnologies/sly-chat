@file:JvmName("PreKeyUtils")
package com.vfpowertech.keytap.core.http.api.prekeys

import com.vfpowertech.keytap.core.crypto.KeyVault
import com.vfpowertech.keytap.core.crypto.axolotl.GeneratedPreKeys
import com.vfpowertech.keytap.core.crypto.axolotl.UserPreKeySet
import com.vfpowertech.keytap.core.crypto.hexify
import com.vfpowertech.keytap.core.crypto.unhexify
import org.whispersystems.libaxolotl.IdentityKey
import org.whispersystems.libaxolotl.state.PreKeyRecord
import org.whispersystems.libaxolotl.state.SignedPreKeyRecord

fun preKeyStorageRequestFromGeneratedPreKeys(
    authToken: String,
    keyVault: KeyVault,
    generatedPreKeys: GeneratedPreKeys
): PreKeyStoreRequest {
    val identityKey = keyVault.identityKeyPair.publicKey.serialize().hexify()

    val signedPreKey = generatedPreKeys.signedPreKey.serialize().hexify()
    val oneTimePreKeys = generatedPreKeys.oneTimePreKeys.map { it.serialize().hexify() }
    val lastResortPreKey = generatedPreKeys.lastResortPreKey.serialize().hexify()

    return PreKeyStoreRequest(authToken, identityKey, signedPreKey, oneTimePreKeys, lastResortPreKey)
}

fun userPreKeySetFromRetrieveResponse(response: PreKeyRetrieveResponse): UserPreKeySet? {
    val keyData = response.keyData ?: return null

    return UserPreKeySet(
        IdentityKey(keyData.identityKey.unhexify(), 0),
        SignedPreKeyRecord(keyData.signedPreKey.unhexify()),
        PreKeyRecord(keyData.preKey.unhexify())
    )
}
