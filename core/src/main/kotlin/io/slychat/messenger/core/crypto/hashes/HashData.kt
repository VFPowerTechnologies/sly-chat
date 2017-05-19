package io.slychat.messenger.core.crypto.hashes

/** Hashed data, along with hashing parameters used to create it. */
class HashData(val hash: ByteArray, val params: HashParams)