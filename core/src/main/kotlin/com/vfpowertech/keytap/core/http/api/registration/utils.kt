package com.vfpowertech.keytap.core.http.api.registration

import com.fasterxml.jackson.databind.ObjectMapper
import com.vfpowertech.keytap.core.crypto.KeyVault
import com.vfpowertech.keytap.core.crypto.hexify

fun registrationRequestFromKeyVault(registrationInfo: RegistrationInfo, keyVault: KeyVault): RegisterRequest {
    val objectMapper = ObjectMapper()

    val hash = keyVault.remotePasswordHash.hexify()
    val hashParams = objectMapper.writeValueAsString(keyVault.remotePasswordHashParams.serialize())

    val publicKey = keyVault.fingerprint
    val serializedKeyVault = objectMapper.writeValueAsString(keyVault.serialize())

    return RegisterRequest(registrationInfo.email, registrationInfo.name, registrationInfo.phoneNumber, hash, hashParams, publicKey, serializedKeyVault)
}
