package io.slychat.messenger.core.crypto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import io.slychat.messenger.core.crypto.ciphers.Key
import io.slychat.messenger.core.crypto.ciphers.decryptBulkData
import io.slychat.messenger.core.crypto.hashes.HashParams
import io.slychat.messenger.core.crypto.hashes.HashType
import io.slychat.messenger.core.crypto.hashes.hashPasswordWithParams
import org.spongycastle.crypto.InvalidCipherTextException
import org.whispersystems.libsignal.IdentityKeyPair
import java.util.*

@JsonIgnoreProperties(ignoreUnknown = true)
class SerializedKeyVault(
    @JsonProperty("version")
    val version: Int,

    @JsonProperty("localPasswordHashParams")
    val localPasswordHashParams: HashParams,

    @JsonProperty("encryptedSecrets")
    val encryptedSecrets: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false

        other as SerializedKeyVault

        if (version != other.version) return false
        if (localPasswordHashParams != other.localPasswordHashParams) return false
        if (!Arrays.equals(encryptedSecrets, other.encryptedSecrets)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = version
        result = 31 * result + localPasswordHashParams.hashCode()
        result = 31 * result + Arrays.hashCode(encryptedSecrets)
        return result
    }

    fun deserialize(password: String): KeyVault {
        val objectMapper = ObjectMapper()
        try {
            val localPasswordHash = Key(hashPasswordWithParams(password, localPasswordHashParams, HashType.LOCAL))

            val derivedKeySpec = DerivedKeySpec(localPasswordHash, HKDFInfoList.keyVault())

            val contents = objectMapper.readValue(
                decryptBulkData(derivedKeySpec, encryptedSecrets),
                SerializedKeyVaultSecrets::class.java
            )

            val identityKeyPair = IdentityKeyPair(contents.serializedIdentityKeyPair)

            return KeyVault(
                identityKeyPair,
                contents.masterKey,
                contents.anonymizingData,
                localPasswordHashParams,
                localPasswordHash
            )
        }
        catch (e: InvalidCipherTextException) {
            throw KeyVaultDecryptionFailedException()
        }
    }
}