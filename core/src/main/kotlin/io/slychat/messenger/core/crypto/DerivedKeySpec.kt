package io.slychat.messenger.core.crypto

import io.slychat.messenger.core.crypto.ciphers.Key
import io.slychat.messenger.core.crypto.ciphers.deriveKey

class DerivedKeySpec(
    private val masterKey: Key,
    private val info: HKDFInfo
) {
    fun derive(keyOutputSizeBits: Int): Key {
        return deriveKey(masterKey, info, keyOutputSizeBits)
    }

    override fun toString(): String {
        return "DerivedKeySpec(info=$info)"
    }
}