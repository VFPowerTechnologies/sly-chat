package io.slychat.messenger.core.crypto.ciphers

/** Indicates the encrypted data was lacking metainfo such as IV, hash, etc. */
class MalformedEncryptedDataException(message: String) : RuntimeException(message)