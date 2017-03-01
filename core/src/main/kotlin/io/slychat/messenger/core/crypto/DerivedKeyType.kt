package io.slychat.messenger.core.crypto

enum class DerivedKeyType {
    //for encrypting AccountLocalInfo, which contains the local root key
    ACCOUNT_LOCAL_INFO,
    REMOTE_ADDRESS_BOOK_ENTRIES,
    //for encrypting upload user metadata
    USER_METADATA
}