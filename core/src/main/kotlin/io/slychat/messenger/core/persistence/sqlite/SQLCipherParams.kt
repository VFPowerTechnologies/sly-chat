package io.slychat.messenger.core.persistence.sqlite

import io.slychat.messenger.core.crypto.DerivedKeySpec

enum class SQLCipherCipher(val s: String, val keySizeBits: Int) {
    AES_256_CBC("AES-256-CBC", 256);

    companion object {
        val defaultCipher: SQLCipherCipher = AES_256_CBC
    }
}

class SQLCipherParams(
    val derivedKeySpec: DerivedKeySpec,
    val cipher: SQLCipherCipher
)