package com.vfpowertech.keytap.core.http.api.registration

import com.fasterxml.jackson.databind.ObjectMapper
import com.vfpowertech.keytap.core.crypto.KeyVault
import com.vfpowertech.keytap.core.crypto.hexify
import com.vfpowertech.keytap.core.require

fun registrationRequestFromKeyVault(registrationInfo: RegistrationInfo, keyVault: KeyVault): RegisterRequest {
    require(keyVault.remotePasswordHash != null, "No remote password hash set")
    require(keyVault.remotePasswordHashParams != null, "No remote password hash params set")

    val objectMapper = ObjectMapper()

    val metaData = hashMapOf("name" to registrationInfo.name, "phone-number" to registrationInfo.phoneNumber)
    val hash = keyVault.remotePasswordHash!!.hexify()
    val hashParams = objectMapper.writeValueAsString(keyVault.remotePasswordHashParams!!.serialize())
    val publicKey = keyVault.fingerprint
    val encryptedPrivateKey = keyVault.getEncryptedPrivateKey().hexify()
    val keyHashParams = objectMapper.writeValueAsString(keyVault.keyPasswordHashParams)
    val keyEncryptionParams = objectMapper.writeValueAsString(keyVault.keyPairCipherParams)

    return RegisterRequest(registrationInfo.email, metaData, hash, hashParams, publicKey, encryptedPrivateKey, keyHashParams, keyEncryptionParams)
}
