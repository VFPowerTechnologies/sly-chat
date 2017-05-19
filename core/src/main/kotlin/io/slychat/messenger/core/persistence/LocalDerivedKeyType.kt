package io.slychat.messenger.core.persistence

/** Derived keys for encryption of local data using the device-local key. */
enum class LocalDerivedKeyType {
    /** Generic data encryption. */
    GENERIC,
    /** SQLCipher. */
    SQLCIPHER
}