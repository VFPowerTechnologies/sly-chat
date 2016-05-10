package com.vfpowertech.keytap.core.crypto

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "version")
@JsonSubTypes(
    JsonSubTypes.Type(SerializedKeyVaultV1::class, name = "1")
)
interface SerializedKeyVault

data class SerializedKeyVaultV1(
    @JsonProperty("encryptedKeyPair") val encryptedKeyPair: String,
    @JsonProperty("keyPasswordHashParams") val keyPasswordHashParams: SerializedCryptoParams,
    @JsonProperty("keyPairCipherParams") val keyPairCipherParams: SerializedCryptoParams,
    @JsonProperty("privateKeyHashParams") val privateKeyHashParams: SerializedCryptoParams,
    @JsonProperty("localDataEncryptionParams") val localDataEncryptionParams: SerializedCryptoParams,
    @JsonProperty("encryptedRemotePasswordHash") val encryptedRemotePasswordHash: String,
    @JsonProperty("remotePasswordHashParams") val remotePasswordHashParams: SerializedCryptoParams
) : SerializedKeyVault