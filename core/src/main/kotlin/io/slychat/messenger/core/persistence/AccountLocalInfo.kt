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
 * @property localMasterKey Used as the root key for all device local data encryption.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class AccountLocalInfo(
    @JsonProperty("sqlCipherCipher")
    val sqlCipherCipher: SQLCipherCipher,
    @JsonProperty("remoteHashParams")
    val remoteHashParams: HashParams,
    //this is just public so jackson can serialize it; never use this directly
    @JsonProperty("localMasterKey")
    val localMasterKey: Key
) {
    companion object {
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

    fun getDerivedKeySpec(type: LocalDerivedKeyType): DerivedKeySpec {
        return DerivedKeySpec(localMasterKey, infoForType(type))
    }
}