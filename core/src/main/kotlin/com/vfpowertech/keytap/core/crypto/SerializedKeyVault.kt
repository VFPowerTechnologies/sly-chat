package com.vfpowertech.keytap.core.crypto

import com.fasterxml.jackson.annotation.JsonProperty

data class SerializedKeyVault(
    @JsonProperty("encryptedKeyPair") val encryptedKeyPair: String,
    @JsonProperty("keyPasswordHashParams") val keyPasswordHashParams: SerializedCryptoParams,
    @JsonProperty("keyPairCipherParams") val keyPairCipherParams: SerializedCryptoParams,
    @JsonProperty("privateKeyHashParams") val privateKeyHashParams: SerializedCryptoParams,
    @JsonProperty("localDataEncryptionParams") val localDataEncryptionParams: SerializedCryptoParams
)