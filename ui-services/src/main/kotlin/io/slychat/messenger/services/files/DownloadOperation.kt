package io.slychat.messenger.services.files

import io.slychat.messenger.core.UserCredentials
import io.slychat.messenger.core.crypto.DecryptInputStream
import io.slychat.messenger.core.crypto.ciphers.CipherList
import io.slychat.messenger.core.files.RemoteFile
import io.slychat.messenger.core.http.api.storage.StorageClient
import io.slychat.messenger.core.persistence.Download
import rx.Subscriber
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

class DownloadOperation(
    private val fileAccess: PlatformFileAccess,
    private val userCredentials: UserCredentials,
    private val download: Download,
    private val file: RemoteFile,
    private val storageClient: StorageClient,
    private val subscriber: Subscriber<in Long>,
    private val isCancelled: AtomicBoolean
) {
    init {
        require(!file.isDeleted) { "Given a deleted file" }
    }

    fun run() {
        val cipher = CipherList.getCipher(file.userMetadata.cipherId)
        val resp = storageClient.downloadFile(userCredentials, file.id) ?: throw FileMissingException(file.id)

        val inputStream = if (download.doDecrypt)
            DecryptInputStream(cipher, file.userMetadata.fileKey, resp.inputStream, file.fileMetadata!!.chunkSize)
        else
            resp.inputStream

        val expectedSize = if (download.doDecrypt)
            file.fileMetadata!!.size
        else
            file.remoteFileSize

        inputStream.use { inputStream ->
            val buffer = ByteArray(8 * 1024)
            fileAccess.openFileForWrite(download.filePath).use { outputStream ->
                var readSoFar = 0L

                while (true) {
                    val read = inputStream.read(buffer)

                    //EOF
                    if (read == -1) {
                        if (readSoFar != expectedSize)
                            throw TruncatedFileException(readSoFar, expectedSize)

                        break
                    }
                    else if (read == 0) {
                        continue
                    }
                    readSoFar += read

                    outputStream.write(buffer, 0, read)
                    subscriber.onNext(read.toLong())

                    if (isCancelled.get() || subscriber.isUnsubscribed)
                        throw CancellationException()
                }
            }
        }
    }
}