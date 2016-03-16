@file:JvmName("ContactsUtils")
package com.vfpowertech.keytap.core.http.api.contacts

import com.vfpowertech.keytap.core.crypto.*
import org.spongycastle.crypto.digests.SHA256Digest

/** Returns the SHA256 hash of the local encryption key and the given email as a hex string. */
fun getEmailHash(keyVault: KeyVault, email: String): String {
    val digester = SHA256Digest()
    val digest = ByteArray(digester.digestSize)

    val key = keyVault.localDataEncryptionKey
    digester.update(key, 0, key.size)
    val b = email.toByteArray(Charsets.UTF_8)
    digester.update(b, 0, b.size)

    digester.doFinal(digest, 0)

    return digest.hexify()
}

fun encryptRemoteContactEntries(keyVault: KeyVault, contacts: List<String>): List<RemoteContactEntry> {
    val encSpec = EncryptionSpec(keyVault.localDataEncryptionKey, keyVault.localDataEncryptionParams)

    return contacts.map { email ->
        val encryptedEmail = encryptDataWithParams(encSpec, email.toByteArray(Charsets.UTF_8)).data.hexify()
        RemoteContactEntry(getEmailHash(keyVault, email), encryptedEmail)
    }
}

fun decryptRemoteContactEntries(keyVault: KeyVault, contacts: List<RemoteContactEntry>): List<String> {
    val encSpec = EncryptionSpec(keyVault.localDataEncryptionKey, keyVault.localDataEncryptionParams)

    return contacts.map { decryptData(encSpec, it.encryptedEmail.unhexify()).toString(Charsets.UTF_8) }
}
