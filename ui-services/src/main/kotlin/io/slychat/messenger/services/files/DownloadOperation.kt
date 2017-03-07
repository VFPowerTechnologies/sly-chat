package io.slychat.messenger.services.files

import io.slychat.messenger.core.UserCredentials
import io.slychat.messenger.core.crypto.DecryptInputStream
import io.slychat.messenger.core.files.RemoteFile
import io.slychat.messenger.core.http.api.storage.StorageClient
import io.slychat.messenger.core.persistence.Download
import rx.Subscriber
import java.io.FileOutputStream
import java.util.concurrent.CancellationException

class DownloadOperation(
    private val userCredentials: UserCredentials,
    private val download: Download,
    private val file: RemoteFile,
    private val storageClient: StorageClient,
    private val subscriber: Subscriber<in Long>
) {
    init {
        require(!file.isDeleted) { "Given a deleted file" }
    }

    fun run() {
        val resp = storageClient.downloadFile(userCredentials, file.id) ?: throw FileMissingException(file.id)

        val inputStream = if (download.doDecrypt)
            DecryptInputStream(file.userMetadata.fileKey.raw, resp.inputStream, file.fileMetadata!!.chunkSize)
        else
            resp.inputStream

        inputStream.use { inputStream ->
            val buffer = ByteArray(8 * 1024)

            FileOutputStream(download.filePath).use { outputStream ->
                while (true) {
                    val read = inputStream.read(buffer)

                    //EOF
                    if (read == -1) {
                        break
                    }
                    else if (read == 0) {
                        continue
                    }

                    outputStream.write(buffer, 0, read)
                    subscriber.onNext(read.toLong())

                    if (subscriber.isUnsubscribed)
                        throw CancellationException()
                }
            }
        }
    }
}