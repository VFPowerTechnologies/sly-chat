package io.slychat.messenger.core.crypto.hashes

import io.slychat.messenger.core.crypto.SerializedCryptoParams

/** Represents params for a hash function. */
interface HashParams {
    /** Algorithm names must be in lowercase. */
    val algorithmName: String

    /** Serialize self. */
    fun serialize(): SerializedCryptoParams
}