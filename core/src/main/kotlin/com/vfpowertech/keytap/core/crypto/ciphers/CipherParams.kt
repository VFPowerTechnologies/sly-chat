package com.vfpowertech.keytap.core.crypto.ciphers

/** Represents params for a cipher. */
interface CipherParams {
    /** Algorithm names must be in lowercase. */
    val algorithmName: String

    /** Serialize parameters. */
    fun serialize(): Map<String, String>
}