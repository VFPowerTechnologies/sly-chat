@file:JvmName("FilesUtil")
package io.slychat.messenger.core.files

import io.slychat.messenger.core.crypto.DerivedKeyType
import io.slychat.messenger.core.crypto.KeyVault
import io.slychat.messenger.core.crypto.ciphers.CipherList
import io.slychat.messenger.core.crypto.ciphers.decryptBulkData
import io.slychat.messenger.core.crypto.ciphers.encryptBulkData
import io.slychat.messenger.core.hexify
import io.slychat.messenger.core.http.api.contacts.md5
import io.slychat.messenger.core.persistence.sqlite.JSONMapper

fun encryptUserMetadata(keyVault: KeyVault, userMetadata: UserMetadata): ByteArray {
    val um = JSONMapper.mapper.writeValueAsBytes(userMetadata)
    val keySpec = keyVault.getDerivedKeySpec(DerivedKeyType.USER_METADATA)
    return encryptBulkData(keySpec, um)
}

fun decryptUserMetadata(keyVault: KeyVault, um: ByteArray): UserMetadata {
    val keySpec = keyVault.getDerivedKeySpec(DerivedKeyType.USER_METADATA)
    val json = decryptBulkData(keySpec, um)
    return JSONMapper.mapper.readValue(json, UserMetadata::class.java)
}

fun encryptFileMetadata(userMetadata: UserMetadata, fileMetadata: FileMetadata): ByteArray {
    val cipher = CipherList.getCipher(userMetadata.cipherId)
    val fm = JSONMapper.mapper.writeValueAsBytes(fileMetadata)
    //we use cipher directly, as we don't need to prepend the id to the encrypted data
    return cipher.encrypt(userMetadata.fileKey, fm)
}

fun decryptFileMetadata(userMetadata: UserMetadata, fm: ByteArray): FileMetadata {
    val cipher = CipherList.getCipher(userMetadata.cipherId)

    val json = cipher.decrypt(userMetadata.fileKey, fm)

    return JSONMapper.mapper.readValue(json, FileMetadata::class.java)
}

fun getFilePathHash(keyVault: KeyVault, userMetadata: UserMetadata): String {
    val pathBytes = "${userMetadata.directory}/${userMetadata.fileName}".toByteArray(Charsets.UTF_8)
    return md5(keyVault.anonymizingData, pathBytes).hexify()
}
