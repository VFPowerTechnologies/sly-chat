package io.slychat.messenger.core.http.api.registration

import com.fasterxml.jackson.databind.ObjectMapper
import io.slychat.messenger.core.crypto.KeyVault
import io.slychat.messenger.core.crypto.hashPasswordForRemoteWithDefaults
import io.slychat.messenger.core.hexify

fun registrationRequestFromKeyVault(registrationInfo: RegistrationInfo, keyVault: KeyVault, password: String): RegisterRequest {
    val remotePasswordHashInfo = hashPasswordForRemoteWithDefaults(password)

    val objectMapper = ObjectMapper()

    val hash = remotePasswordHashInfo.hash.hexify()
    val hashParams = objectMapper.writeValueAsString(remotePasswordHashInfo.params)

    val publicKey = keyVault.fingerprint
    val serializedKeyVault = objectMapper.writeValueAsString(keyVault.serialize())

    return RegisterRequest(registrationInfo.email, registrationInfo.name, registrationInfo.phoneNumber, hash, hashParams, publicKey, serializedKeyVault)
}
