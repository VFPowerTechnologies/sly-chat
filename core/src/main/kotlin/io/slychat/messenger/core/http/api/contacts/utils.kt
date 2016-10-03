@file:JvmName("ContactsUtils")
package io.slychat.messenger.core.http.api.contacts

import com.fasterxml.jackson.databind.ObjectMapper
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.crypto.DerivedKeySpec
import io.slychat.messenger.core.crypto.DerivedKeyType
import io.slychat.messenger.core.crypto.HKDFInfoList
import io.slychat.messenger.core.crypto.KeyVault
import io.slychat.messenger.core.crypto.ciphers.CipherList
import io.slychat.messenger.core.crypto.ciphers.decryptBulkData
import io.slychat.messenger.core.crypto.ciphers.encryptBulkData
import io.slychat.messenger.core.hexify
import io.slychat.messenger.core.persistence.AddressBookUpdate
import io.slychat.messenger.core.persistence.GroupId
import io.slychat.messenger.core.persistence.RemoteAddressBookEntry
import org.spongycastle.crypto.digests.MD5Digest
import org.spongycastle.crypto.digests.SHA256Digest

private fun UserId.toByteArray(): ByteArray =
    long.toString().toByteArray(Charsets.UTF_8)

/** Returns the SHA256 hash of the local encryption key and the given user id as a hex string. */
private fun getEmailHash(keyVault: KeyVault, userId: UserId): String {
    val digester = SHA256Digest()
    val digest = ByteArray(digester.digestSize)

    digester.update(keyVault.anonymizingData, 0, keyVault.anonymizingData.size)
    val b = userId.toByteArray()
    digester.update(b, 0, b.size)

    digester.doFinal(digest, 0)

    return digest.hexify()
}

private fun getGroupHash(keyVault: KeyVault, groupId: GroupId): String {
    val digester = SHA256Digest()
    val digest = ByteArray(digester.digestSize)

    digester.update(keyVault.anonymizingData, 0, keyVault.anonymizingData.size)
    val b = groupId.string.toByteArray()
    digester.update(b, 0, b.size)

    digester.doFinal(digest, 0)

    return digest.hexify()

}

//first we create RemoteContactEntryData, then serialize them to json, and then encrypt them
//afterwards we then store the encrypted value along with the user id hash in a RemoteContactEntry
fun encryptRemoteAddressBookEntries(keyVault: KeyVault, updates: List<AddressBookUpdate>): List<RemoteAddressBookEntry> {
    val cipher = CipherList.defaultDataEncryptionCipher
    val derivedKey = keyVault.deriveKeyFor(DerivedKeyType.REMOTE_ADDRESS_BOOK_ENTRIES, cipher)

    val objectMapper = ObjectMapper()

    return updates.map { update ->
        val hash = when (update) {
            is AddressBookUpdate.Contact -> getEmailHash(keyVault, update.userId)
            is AddressBookUpdate.Group -> getGroupHash(keyVault, update.groupId)
        }

        val encryptedData = encryptBulkData(cipher, derivedKey, objectMapper.writeValueAsBytes(update))
        RemoteAddressBookEntry(hash, encryptedData)
    }
}

fun decryptRemoteAddressBookEntries(keyVault: KeyVault, entries: List<RemoteAddressBookEntry>): List<AddressBookUpdate> {
    val objectMapper = ObjectMapper()

    val derivedKeySpec = DerivedKeySpec(keyVault.masterKey, HKDFInfoList.remoteAddressBookEntries())

    return entries.map { e ->
        val raw = decryptBulkData(derivedKeySpec, e.encryptedData)
        objectMapper.readValue(raw, AddressBookUpdate::class.java)
    }
}

fun updateRequestFromAddressBookUpdates(addressBookHash: String, keyVault: KeyVault, addressBookUpdates: List<AddressBookUpdate>): UpdateAddressBookRequest {
    val updates = encryptRemoteAddressBookEntries(keyVault, addressBookUpdates)

    return UpdateAddressBookRequest(addressBookHash, updates)
}

fun md5(data: ByteArray): ByteArray {
    val digester = MD5Digest()
    val digest = ByteArray(digester.digestSize)

    digester.update(data, 0, data.size)

    digester.doFinal(digest, 0)

    return digest
}

inline fun md5Fold(body: ((ByteArray) -> Unit) -> Unit): ByteArray {
    val digester = MD5Digest()
    val digest = ByteArray(digester.digestSize)

    val updater = { piece: ByteArray ->
        digester.update(piece, 0, piece.size)
    }

    body(updater)

    digester.doFinal(digest, 0)

    return digest
}

fun hashFromRemoteAddressBookEntries(remoteAddressBookEntries: Collection<RemoteAddressBookEntry>): String {
    return md5Fold { updater ->
        remoteAddressBookEntries.forEach {
            updater(md5(it.encryptedData))
        }
    }.hexify()
}
