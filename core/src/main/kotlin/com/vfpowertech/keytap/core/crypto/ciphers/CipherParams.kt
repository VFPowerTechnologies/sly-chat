package com.vfpowertech.keytap.core.crypto.ciphers

import com.vfpowertech.keytap.core.crypto.SerializedCryptoParams

/** Represents params for a cipher. */
interface CipherParams {
    /** Algorithm names must be in lowercase. */
    val algorithmName: String

    /** */
    val keyType: String

    /** Serialize parameters. */
    fun serialize(): SerializedCryptoParams
}