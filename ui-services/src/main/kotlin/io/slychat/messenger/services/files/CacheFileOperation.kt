package io.slychat.messenger.services.files

import io.slychat.messenger.core.crypto.EncryptInputStream
import io.slychat.messenger.core.crypto.ciphers.CipherList
import io.slychat.messenger.core.files.RemoteFile
import io.slychat.messenger.core.persistence.Upload
import rx.Subscriber
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

class CacheFileOperation(
    private val fileAccess: PlatformFileAccess,
    private val upload: Upload,
    private val file: RemoteFile,
    private val subscriber: Subscriber<in Long>,
    private val isCancelled: AtomicBoolean
) {
    init {
        require(upload.cachePath != null) { "No cache path set for upload" }
        require(!file.isDeleted) { "Given a deleted file" }
    }

    fun run() {
        val cipher = CipherList.getCipher(file.userMetadata.cipherId)

        fileAccess.openFileForRead(upload.filePath).use {
            val dataInputStream = EncryptInputStream(cipher, file.userMetadata.fileKey, it, file.fileMetadata!!.chunkSize)

            var wasCancelled = false

            val cachePath = upload.cachePath!!

            fileAccess.openFileForWrite(cachePath).use { outputStream ->
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
                fileAccess.delete(cachePath)
                throw CancellationException()
            }
        }
    }
}
