package io.slychat.messenger.core.persistence

/** Derived keys for encryption of local data using the device-local key. */
enum class LocalDerivedKeyType {
    GENERIC,
    SQLCIPHER
}