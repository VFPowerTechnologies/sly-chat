package com.vfpowertech.keytap.core.crypto.hashes

/** Represents params for a hash function. */
interface HashParams {
    /** Algorithm names must be in lowercase. */
    val algorithmName: String

    /** Serialize parameters. */
    fun serialize(): Map<String, String>
}