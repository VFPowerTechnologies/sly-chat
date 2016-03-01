package com.vfpowertech.keytap.core.http.api.registration

import com.fasterxml.jackson.databind.ObjectMapper
import com.vfpowertech.keytap.core.crypto.KeyVault
import com.vfpowertech.keytap.core.crypto.hexify
import com.vfpowertech.keytap.core.require

fun registrationRequestFromKeyVault(registrationInfo: RegistrationInfo, keyVault: KeyVault): RegisterRequest {
    require(keyVault.remotePasswordHash != null, "No remote password hash set")
    require(keyVault.remotePasswordHashParams != null, "No remote password hash params set")

    val objectMapper = ObjectMapper()

    val hash = keyVault.remotePasswordHash!!.hexify()
    val hashParams = objectMapper.writeValueAsString(keyVault.remotePasswordHashParams!!.serialize())
    val publicKey = keyVault.fingerprint
    val serializedKeyVault = objectMapper.writeValueAsString(keyVault.serialize())

    return RegisterRequest(registrationInfo.email, registrationInfo.name, registrationInfo.phoneNumber, hash, hashParams, publicKey, serializedKeyVault)
}
