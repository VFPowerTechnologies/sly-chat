package io.slychat.messenger.services.files

import io.slychat.messenger.core.crypto.EncryptInputStream
import io.slychat.messenger.core.crypto.ciphers.CipherList
import io.slychat.messenger.core.files.RemoteFile
import io.slychat.messenger.core.persistence.Upload
import rx.Subscriber

class CacheFileOperation(
    private val fileAccess: PlatformFileAccess,
    private val upload: Upload,
    private val file: RemoteFile,
    private val subscriber: Subscriber<in Long>
) {
    init {
        require(upload.cachePath != null) { "No cache path set for upload" }
        require(!file.isDeleted) { "Given a deleted file" }
    }

    fun run() {
        val cipher = CipherList.getCipher(file.userMetadata.cipherId)

        fileAccess.openFileForRead(upload.filePath).use {
            val dataInputStream = EncryptInputStream(cipher, file.userMetadata.fileKey, it, file.fileMetadata!!.chunkSize)

            fileAccess.openFileForWrite(upload.cachePath!!).use { outputStream ->
                dataInputStream.copyTo(outputStream)
            }
        }
    }
}
