package io.slychat.messenger.core.persistence.sqlite

import io.slychat.messenger.core.crypto.DerivedKeySpec

/** Cipher for usage with SQLCipher. Currently only AES-256-CBC is available. */
enum class SQLCipherCipher(val s: String, val keySizeBits: Int) {
    AES_256_CBC("AES-256-CBC", 256);

    companion object {
        /** Default SQLCipher for new installations. */
        val defaultCipher: SQLCipherCipher = AES_256_CBC
    }
}

class SQLCipherParams(
    val derivedKeySpec: DerivedKeySpec,
    val cipher: SQLCipherCipher
)