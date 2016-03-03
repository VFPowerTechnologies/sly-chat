package com.vfpowertech.keytap.core.crypto

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

//encryptedRemotePasswordHash and remotePasswordHashParams are only stored locally; they're never sent remotely
@JsonInclude(JsonInclude.Include.NON_NULL)
data class SerializedKeyVault(
    @JsonProperty("encryptedKeyPair") val encryptedKeyPair: String,
    @JsonProperty("keyPasswordHashParams") val keyPasswordHashParams: SerializedCryptoParams,
    @JsonProperty("keyPairCipherParams") val keyPairCipherParams: SerializedCryptoParams,
    @JsonProperty("privateKeyHashParams") val privateKeyHashParams: SerializedCryptoParams,
    @JsonProperty("localDataEncryptionParams") val localDataEncryptionParams: SerializedCryptoParams,
    @JsonProperty("encryptedRemotePasswordHash") val encryptedRemotePasswordHash: String?,
    @JsonProperty("remotePasswordHashParams") val remotePasswordHashParams: SerializedCryptoParams?
)