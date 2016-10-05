package io.slychat.messenger.core.crypto.hashes

import org.spongycastle.crypto.generators.SCrypt

enum class HashType {
    LOCAL,
    REMOTE
}

private const val LOCAL_PASSWORD_INDICATOR: Byte = 0x11
private const val REMOTE_PASSWORD_INDICATOR: Byte = 0x22

/**
 * Derive a key from a password, either for remote authentication or local (key vault) encryption.
 *
 * @throws IllegalArgumentException If the type of CryptoParams is unknown
 */
fun hashPasswordWithParams(password: String, params: HashParams, type: HashType): ByteArray {
    //if two users share the same password, and the local IV for user A and the remote IV for user B are also equal,
    //we want the passwords to yield different keys
    val indicatorByte = when (type) {
        HashType.LOCAL -> LOCAL_PASSWORD_INDICATOR
        HashType.REMOTE -> REMOTE_PASSWORD_INDICATOR
    }

    val data = password.toByteArray(Charsets.UTF_8)
    val fullData = ByteArray(data.size + 1)
    System.arraycopy(
        data,
        0,
        fullData,
        0,
        data.size
    )

    fullData[data.size] = indicatorByte

    return when (params) {
        is HashParams.SCrypt ->
            SCrypt.generate(fullData, params.salt, params.n, params.r, params.p, params.keyLengthBits * 8)
    }
}
