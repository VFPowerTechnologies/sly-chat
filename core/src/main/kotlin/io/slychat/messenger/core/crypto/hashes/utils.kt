package io.slychat.messenger.core.crypto.hashes

import org.spongycastle.crypto.Digest
import org.spongycastle.crypto.digests.SHA256Digest
import org.spongycastle.crypto.generators.BCrypt
import org.spongycastle.crypto.generators.SCrypt
import org.spongycastle.util.encoders.Base64

/**
 * Used to hash a password for authentication with the remote server using the provided params.
 *
 * @throws IllegalArgumentException If the type of CryptoParams is unknown
 */
fun hashPasswordWithParams(password: String, params: HashParams): ByteArray = when (params) {
    is BCryptParams -> {
        //this design is from passlib, to get around the 72 password length limitation (incase)
        //see https://pythonhosted.org/passlib/lib/passlib.hash.bcrypt_sha256.html
        val digester = SHA256Digest()
        val hash = digester.processInput(password.toByteArray(Charsets.UTF_8))

        //44b string
        val encoded = Base64.encode(hash)

        //password must be nul-terminated to prevent collisions in repeated passwords (eg: test and testtest)
        val input = ByteArray(encoded.size+1)
        System.arraycopy(encoded, 0, input, 0, encoded.size)

        BCrypt.generate(input, params.salt, params.cost)
    }
    else -> hashDataWithParams(password.toByteArray(Charsets.UTF_8), params)
}

fun hashDataWithParams(data: ByteArray, params: HashParams): ByteArray = when (params) {
    is SHA256Params -> {
        val digester = SHA256Digest()
        val salt = params.salt
        val toHash = if (salt.size > 0) {
            val buffer = ByteArray(data.size+salt.size)
            System.arraycopy(data, 0, buffer, 0, data.size)
            System.arraycopy(salt, 0, buffer, data.size, salt.size)
            buffer
        }
        else
            data

        digester.processInput(toHash)
    }

    is SCryptParams ->
        SCrypt.generate(data, params.salt, params.N, params.r, params.p, params.keyLength)

    else -> throw IllegalArgumentException("Unknown data hash algorithm: ${params.algorithmName}")
}

private fun Digest.processInput(input: ByteArray): ByteArray {
    val output = ByteArray(digestSize)
    update(input, 0, input.size)
    doFinal(output, 0)
    return output
}