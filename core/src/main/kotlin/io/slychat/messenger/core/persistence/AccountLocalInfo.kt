package io.slychat.messenger.core.persistence

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import io.slychat.messenger.core.crypto.DerivedKeySpec
import io.slychat.messenger.core.crypto.HKDFInfo
import io.slychat.messenger.core.crypto.HKDFInfoList
import io.slychat.messenger.core.crypto.ciphers.Key
import io.slychat.messenger.core.crypto.generateLocalMasterKey
import io.slychat.messenger.core.crypto.hashes.HashParams
import io.slychat.messenger.core.persistence.sqlite.SQLCipherCipher

/**
 * Account device-specific encryption data.
 *
 * @property sqlCipherCipher Encryption cipher used by current SQLCipher database.
 * @property remoteHashParams Local copy of remote hash parameters for authentication.
 * @property localMasterKey Used as the root key for all device local data encryption. This is only public so jackson can serialize it; use getDerivedKeySpec instead of accessing this directly.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class AccountLocalInfo(
    @JsonProperty("sqlCipherCipher")
    val sqlCipherCipher: SQLCipherCipher,
    @JsonProperty("remoteHashParams")
    val remoteHashParams: HashParams,
    @JsonProperty("localMasterKey")
    val localMasterKey: Key
) {
    companion object {
        /** Generate AccountLocalInfo for a new account. */
        fun generate(remoteHashParams: HashParams): AccountLocalInfo =
            AccountLocalInfo(
                SQLCipherCipher.defaultCipher,
                remoteHashParams,
                generateLocalMasterKey()
            )
    }

    private fun infoForType(type: LocalDerivedKeyType): HKDFInfo = when (type) {
        LocalDerivedKeyType.GENERIC -> HKDFInfoList.localData()
        LocalDerivedKeyType.SQLCIPHER -> HKDFInfoList.sqlcipher()
    }

    /** Returns a [io.slychat.messenger.core.crypto.DerivedKeySpec] for the given type of data. */
    fun getDerivedKeySpec(type: LocalDerivedKeyType): DerivedKeySpec {
        return DerivedKeySpec(localMasterKey, infoForType(type))
    }
}