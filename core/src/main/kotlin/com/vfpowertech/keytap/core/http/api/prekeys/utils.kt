@file:JvmName("PreKeyUtils")
package com.vfpowertech.keytap.core.http.api.prekeys

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.vfpowertech.keytap.core.crypto.KeyVault
import com.vfpowertech.keytap.core.crypto.hexify
import com.vfpowertech.keytap.core.crypto.signal.GeneratedPreKeys
import com.vfpowertech.keytap.core.crypto.signal.UserPreKeySet
import com.vfpowertech.keytap.core.crypto.unhexify
import org.whispersystems.libsignal.ecc.Curve
import org.whispersystems.libsignal.ecc.ECPublicKey
import org.whispersystems.libsignal.state.PreKeyRecord
import org.whispersystems.libsignal.state.SignedPreKeyRecord

data class UnsignedPreKeyPublicData(
    @JsonProperty("id")
    val id: Int,
    @JsonProperty("key")
    val key: ByteArray
) {
    @JsonIgnore
    fun getECPublicKey(): ECPublicKey =
        Curve.decodePoint(key, 0)
}

fun PreKeyRecord.toPublicData(): UnsignedPreKeyPublicData =
    UnsignedPreKeyPublicData(
        id,
        keyPair.publicKey.serialize()
    )

data class SignedPreKeyPublicData(
    @JsonProperty("id")
    val id: Int,
    @JsonProperty("key")
    val key: ByteArray,
    @JsonProperty("signature")
    val signature: ByteArray
) {
    @JsonIgnore
    fun getECPublicKey(): ECPublicKey =
        Curve.decodePoint(key, 0)
}

fun SignedPreKeyRecord.toPublicData(): SignedPreKeyPublicData =
    SignedPreKeyPublicData(
        id,
        keyPair.publicKey.serialize(),
        signature
    )

fun serializePreKey(preKeyRecord: PreKeyRecord): String =
    ObjectMapper().writeValueAsString(preKeyRecord.toPublicData())

fun serializeOneTimePreKeys(oneTimePreKeys: List<PreKeyRecord>): List<String> =
    oneTimePreKeys.map { serializePreKey(it) }

fun serializeSignedPreKey(signedPreKeyRecord: SignedPreKeyRecord): String =
    ObjectMapper().writeValueAsString(signedPreKeyRecord.toPublicData())

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
        SignedPreKeyRecord(keyData.signedPreKey.unhexify()),
        PreKeyRecord(keyData.preKey.unhexify())
    )
}
