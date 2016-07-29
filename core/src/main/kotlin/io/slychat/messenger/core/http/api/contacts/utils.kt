@file:JvmName("ContactsUtils")
package io.slychat.messenger.core.http.api.contacts

import com.fasterxml.jackson.databind.ObjectMapper
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.crypto.*
import io.slychat.messenger.core.persistence.RemoteContactUpdate
import org.spongycastle.crypto.digests.SHA256Digest

private fun UserId.toByteArray(): ByteArray =
    long.toString().toByteArray(Charsets.UTF_8)

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

//first we create RemoteContactEntryData, then serialize them to json, and then encrypt them
//afterwards we then store the encrypted value along with the user id hash in a RemoteContactEntry
fun encryptRemoteContactEntries(keyVault: KeyVault, updates: List<RemoteContactUpdate>): List<RemoteContactEntry> {
    val encSpec = EncryptionSpec(keyVault.localDataEncryptionKey, keyVault.localDataEncryptionParams)

    val objectMapper = ObjectMapper()

    return updates.map { update ->
        val userId = update.userId
        val data = RemoteContactEntryData(userId, update.allowedMessageLevel)
        val encryptedContactData = encryptDataWithParams(encSpec, objectMapper.writeValueAsBytes(data)).data
        RemoteContactEntry(getEmailHash(keyVault, userId), encryptedContactData)
    }
}

fun decryptRemoteContactEntries(keyVault: KeyVault, entries: List<RemoteContactEntry>): List<RemoteContactUpdate> {
    val encSpec = EncryptionSpec(keyVault.localDataEncryptionKey, keyVault.localDataEncryptionParams)

    val objectMapper = ObjectMapper()

    return entries.map { e ->
        val raw = decryptData(encSpec, e.encryptedContactData)
        val entryData = objectMapper.readValue(raw, RemoteContactEntryData::class.java)
        RemoteContactUpdate(entryData.userId, entryData.allowedMessageLevel)
    }
}

fun updateRequestFromRemoteContactUpdates(keyVault: KeyVault, remoteContactUpdates: List<RemoteContactUpdate>): UpdateAddressBookRequest {
    val updates = encryptRemoteContactEntries(keyVault, remoteContactUpdates)

    return UpdateAddressBookRequest(updates)
}