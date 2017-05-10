package io.slychat.messenger.services.files

import io.slychat.messenger.core.crypto.EncryptInputStream
import io.slychat.messenger.core.crypto.ciphers.CipherList
import io.slychat.messenger.core.files.RemoteFile
import io.slychat.messenger.core.persistence.Upload
import io.slychat.messenger.services.files.cache.AttachmentCache
import rx.Subscriber
import java.io.FileOutputStream
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

class CacheFileOperation(
    private val fileAccess: PlatformFileAccess,
    private val upload: Upload,
    private val file: RemoteFile,
    private val subscriber: Subscriber<in Long>,
    private val isCancelled: AtomicBoolean,
    private val attachmentCache: AttachmentCache
) {
    init {
        require(upload.cachePath != null) { "No cache path set for upload" }
        require(!file.isDeleted) { "Given a deleted file" }
    }

    fun run() {
        val cipher = CipherList.getCipher(file.userMetadata.cipherId)

        fileAccess.openFileForRead(upload.filePath) {
            val dataInputStream = EncryptInputStream(cipher, file.userMetadata.fileKey, it, file.fileMetadata!!.chunkSize)

            var wasCancelled = false

            val outputPath = attachmentCache.getPendingPathForFile(file.id)

            FileOutputStream(outputPath).use { outputStream ->
                val buffer = ByteArray(8 * 1024)

                while (true) {
                    val read = dataInputStream.read(buffer)
                    if (read <= 0)
                        break

                    outputStream.write(buffer, 0, read)
                    //TODO should probably emit this in larger batches
                    //subscriber.onNext(read.toLong())

                    if (isCancelled.get()) {
                        wasCancelled = true
                        break
                    }
                }
            }

            if (wasCancelled) {
                outputPath.delete()
                throw CancellationException()
            }

            attachmentCache.markOriginalComplete(listOf(file.id)).get()
        }
    }
}
