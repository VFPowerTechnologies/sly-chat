package com.vfpowertech.keytap.core.http.api.prekeys

import com.vfpowertech.keytap.core.crypto.KeyVault
import com.vfpowertech.keytap.core.crypto.axolotl.GeneratedPreKeys
import com.vfpowertech.keytap.core.crypto.hexify

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