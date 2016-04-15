@file:JvmName("ContactsUtils")
package com.vfpowertech.keytap.core.http.api.contacts

import com.vfpowertech.keytap.core.UserId
import com.vfpowertech.keytap.core.crypto.*
import org.spongycastle.crypto.digests.SHA256Digest

private fun UserId.toByteArray(): ByteArray =
    id.toString().toByteArray(Charsets.UTF_8)

/** Returns the SHA256 hash of the local encryption key and the given user id as a hex string. */
fun getEmailHash(keyVault: KeyVault, userId: UserId): String {
    val digester = SHA256Digest()
    val digest = ByteArray(digester.digestSize)

    val key = keyVault.localDataEncryptionKey
    digester.update(key, 0, key.size)
    val b = userId.toByteArray()
    digester.update(b, 0, b.size)

    digester.doFinal(digest, 0)

    return digest.hexify()
}

fun encryptRemoteContactEntries(keyVault: KeyVault, contacts: List<UserId>): List<RemoteContactEntry> {
    val encSpec = EncryptionSpec(keyVault.localDataEncryptionKey, keyVault.localDataEncryptionParams)

    return contacts.map { userId ->
        val encryptedEmail = encryptDataWithParams(encSpec, userId.toByteArray()).data.hexify()
        RemoteContactEntry(getEmailHash(keyVault, userId), encryptedEmail)
    }
}

fun decryptRemoteContactEntries(keyVault: KeyVault, contacts: List<RemoteContactEntry>): List<UserId> {
    val encSpec = EncryptionSpec(keyVault.localDataEncryptionKey, keyVault.localDataEncryptionParams)

    return contacts.map {
        UserId(decryptData(encSpec, it.encryptedUserId.unhexify()).toString(Charsets.UTF_8).toLong())
    }
}
