@file:JvmName("FilesUtil")
package io.slychat.messenger.core.files

import io.slychat.messenger.core.crypto.DerivedKeyType
import io.slychat.messenger.core.crypto.KeyVault
import io.slychat.messenger.core.crypto.ciphers.CipherList
import io.slychat.messenger.core.crypto.ciphers.decryptBulkData
import io.slychat.messenger.core.crypto.ciphers.encryptBulkData
import io.slychat.messenger.core.hexify
import io.slychat.messenger.core.http.api.contacts.md5
import io.slychat.messenger.core.persistence.json.JSONMapper
import java.util.*

/** Encrypt UserMetadata for upload to server. */
fun encryptUserMetadata(keyVault: KeyVault, userMetadata: UserMetadata): ByteArray {
    val um = JSONMapper.mapper.writeValueAsBytes(userMetadata)
    val keySpec = keyVault.getDerivedKeySpec(DerivedKeyType.USER_METADATA)
    return encryptBulkData(keySpec, um)
}

/** Decrypt UserMedata received from server. */
fun decryptUserMetadata(keyVault: KeyVault, um: ByteArray): UserMetadata {
    val keySpec = keyVault.getDerivedKeySpec(DerivedKeyType.USER_METADATA)
    val json = decryptBulkData(keySpec, um)
    return JSONMapper.mapper.readValue(json, UserMetadata::class.java)
}

/** Encrypt FileMetadata for upload to server. */
fun encryptFileMetadata(userMetadata: UserMetadata, fileMetadata: FileMetadata): ByteArray {
    val cipher = CipherList.getCipher(userMetadata.cipherId)
    val fm = JSONMapper.mapper.writeValueAsBytes(fileMetadata)
    //we use cipher directly, as we don't need to prepend the id to the encrypted data
    return cipher.encrypt(userMetadata.fileKey, fm)
}

/** Decrypt FileMetadata received from server. */
fun decryptFileMetadata(userMetadata: UserMetadata, fm: ByteArray): FileMetadata {
    val cipher = CipherList.getCipher(userMetadata.cipherId)

    val json = cipher.decrypt(userMetadata.fileKey, fm)

    return JSONMapper.mapper.readValue(json, FileMetadata::class.java)
}

/** Generate a file path hash. Used on the server to uniquely identify a file path for the current account without exposing its actual value. */
fun getFilePathHash(keyVault: KeyVault, userMetadata: UserMetadata): String {
    val pathBytes = "${userMetadata.directory}/${userMetadata.fileName}".toLowerCase(Locale.ROOT).toByteArray(Charsets.UTF_8)
    return md5(keyVault.anonymizingData, pathBytes).hexify()
}
