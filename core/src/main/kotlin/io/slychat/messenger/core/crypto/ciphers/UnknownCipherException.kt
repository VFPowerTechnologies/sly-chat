package io.slychat.messenger.core.crypto.ciphers

class UnknownCipherException(val cipherId: CipherId) : RuntimeException("Unknown cipher id: $cipherId")