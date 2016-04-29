@file:JvmName("PreKeyUtils")
package com.vfpowertech.keytap.core.http.api.prekeys

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.vfpowertech.keytap.core.crypto.KeyVault
import com.vfpowertech.keytap.core.crypto.hexify
import com.vfpowertech.keytap.core.crypto.signal.GeneratedPreKeys
import com.vfpowertech.keytap.core.crypto.unhexify
import org.whispersystems.libsignal.IdentityKey
import org.whispersystems.libsignal.ecc.Curve
import org.whispersystems.libsignal.ecc.ECPublicKey
import org.whispersystems.libsignal.state.PreKeyBundle
import org.whispersystems.libsignal.state.PreKeyRecord
import org.whispersystems.libsignal.state.SignedPreKeyRecord

data class SerializedPreKeyBundle(
    @JsonProperty("signedPreKey")
    val signedPreKey: String,

    @param:JsonProperty("oneTimePreKeys")
    val oneTimePreKeys: List<String>,

    @JsonProperty("lastResortPreKey")
    val lastResortPreKey: String
)

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

fun serializedBundleFromGeneratedPreKeys(
    generatedPreKeys: GeneratedPreKeys,
    lastResortPreKey: PreKeyRecord
): SerializedPreKeyBundle {
    return SerializedPreKeyBundle(
        serializeSignedPreKey(generatedPreKeys.signedPreKey),
        serializeOneTimePreKeys(generatedPreKeys.oneTimePreKeys),
        serializePreKey(lastResortPreKey)
    )
}

fun preKeyStorageRequestFromGeneratedPreKeys(
    authToken: String,
    registrationId: Int,
    keyVault: KeyVault,
    generatedPreKeys: GeneratedPreKeys,
    lastResortPreKey: PreKeyRecord
): PreKeyStoreRequest {
    val identityKey = keyVault.identityKeyPair.publicKey.serialize().hexify()

    val bundle = serializedBundleFromGeneratedPreKeys(generatedPreKeys, lastResortPreKey)

    return PreKeyStoreRequest(authToken, registrationId, identityKey, bundle)
}

fun SerializedPreKeySet.toPreKeyBundle(deviceId: Int): PreKeyBundle {
    val objectMapper = ObjectMapper()
    val registrationId = registrationId

    val oneTimePreKey = objectMapper.readValue(preKey, UnsignedPreKeyPublicData::class.java)
    val signedPreKey = objectMapper.readValue(signedPreKey, SignedPreKeyPublicData::class.java)
    return PreKeyBundle(
        registrationId,
        deviceId,
        oneTimePreKey.id,
        oneTimePreKey.getECPublicKey(),
        signedPreKey.id,
        signedPreKey.getECPublicKey(),
        signedPreKey.signature,
        IdentityKey(publicKey.unhexify(), 0)
    )
}
