@file:JvmName("ContactsUtils")
package io.slychat.messenger.core.http.api.contacts

import com.fasterxml.jackson.databind.ObjectMapper
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.crypto.*
import io.slychat.messenger.core.persistence.AddressBookUpdate
import io.slychat.messenger.core.persistence.GroupId
import io.slychat.messenger.core.persistence.RemoteAddressBookEntry
import org.spongycastle.crypto.digests.SHA256Digest

private fun UserId.toByteArray(): ByteArray =
    long.toString().toByteArray(Charsets.UTF_8)

/** Returns the SHA256 hash of the local encryption key and the given user id as a hex string. */
private fun getEmailHash(keyVault: KeyVault, userId: UserId): String {
    val digester = SHA256Digest()
    val digest = ByteArray(digester.digestSize)

    val key = keyVault.localDataEncryptionKey
    digester.update(key, 0, key.size)
    val b = userId.toByteArray()
    digester.update(b, 0, b.size)

    digester.doFinal(digest, 0)

    return digest.hexify()
}

private fun getGroupHash(keyVault: KeyVault, groupId: GroupId): String {
    val digester = SHA256Digest()
    val digest = ByteArray(digester.digestSize)

    val key = keyVault.localDataEncryptionKey
    digester.update(key, 0, key.size)
    val b = groupId.string.toByteArray()
    digester.update(b, 0, b.size)

    digester.doFinal(digest, 0)

    return digest.hexify()

}

//first we create RemoteContactEntryData, then serialize them to json, and then encrypt them
//afterwards we then store the encrypted value along with the user id hash in a RemoteContactEntry
fun encryptRemoteAddressBookEntries(keyVault: KeyVault, updates: List<AddressBookUpdate>): List<RemoteAddressBookEntry> {
    val encSpec = EncryptionSpec(keyVault.localDataEncryptionKey, keyVault.localDataEncryptionParams)

    val objectMapper = ObjectMapper()

    return updates.map { update ->
        val hash = when (update) {
            is AddressBookUpdate.Contact -> getEmailHash(keyVault, update.userId)
            is AddressBookUpdate.Group -> getGroupHash(keyVault, update.groupId)
        }

        val encryptedData = encryptDataWithParams(encSpec, objectMapper.writeValueAsBytes(update)).data
        RemoteAddressBookEntry(hash, encryptedData)
    }
}

fun decryptRemoteAddressBookEntries(keyVault: KeyVault, entries: List<RemoteAddressBookEntry>): List<AddressBookUpdate> {
    val encSpec = EncryptionSpec(keyVault.localDataEncryptionKey, keyVault.localDataEncryptionParams)

    val objectMapper = ObjectMapper()

    return entries.map { e ->
        val raw = decryptData(encSpec, e.encryptedData)
        objectMapper.readValue(raw, AddressBookUpdate::class.java)
    }
}

fun updateRequestFromAddressBookUpdates(keyVault: KeyVault, addressBookUpdates: List<AddressBookUpdate>): UpdateAddressBookRequest {
    val updates = encryptRemoteAddressBookEntries(keyVault, addressBookUpdates)

    return UpdateAddressBookRequest(updates)
}