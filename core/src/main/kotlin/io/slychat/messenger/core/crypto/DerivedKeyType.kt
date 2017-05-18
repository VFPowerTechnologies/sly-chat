package io.slychat.messenger.core.crypto

/** Derived keys for encryption of data using the key vault master key. */
enum class DerivedKeyType {
    //for encrypting AccountLocalInfo, which contains the local root key
    ACCOUNT_LOCAL_INFO,
    REMOTE_ADDRESS_BOOK_ENTRIES,
    //for encrypting upload user metadata
    USER_METADATA
}