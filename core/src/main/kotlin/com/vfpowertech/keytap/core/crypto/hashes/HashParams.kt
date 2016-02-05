package com.vfpowertech.keytap.core.crypto.hashes

import com.vfpowertech.keytap.core.crypto.SerializedCryptoParams

/** Represents params for a hash function. */
interface HashParams {
    /** Algorithm names must be in lowercase. */
    val algorithmName: String

    /** Serialize self. */
    fun serialize(): SerializedCryptoParams
}