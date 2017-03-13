package io.slychat.messenger.services.files

import io.slychat.messenger.core.MD5InputStream
import io.slychat.messenger.core.ProgressOutputStream
import io.slychat.messenger.core.SectionInputStream
import io.slychat.messenger.core.UserCredentials
import io.slychat.messenger.core.crypto.EncryptInputStream
import io.slychat.messenger.core.crypto.ciphers.CipherList
import io.slychat.messenger.core.files.RemoteFile
import io.slychat.messenger.core.http.api.upload.UploadClient
import io.slychat.messenger.core.persistence.Upload
import io.slychat.messenger.core.persistence.UploadPart
import rx.Subscriber
import rx.subscriptions.Subscriptions
import java.util.concurrent.atomic.AtomicBoolean

class UploadPartOperation(
    private val fileAccess: PlatformFileAccess,
    private val userCredentials: UserCredentials,
    private val upload: Upload,
    private val part: UploadPart,
    private val file: RemoteFile,
    private val uploadClient: UploadClient,
    private val subscriber: Subscriber<in Long>
) {
    fun run() {
        //TODO handle missing file (FileNotFoundException), and then any other exception that's raise
        fileAccess.openFileForRead(upload.filePath).use { fileInputStream ->
            val limiter = SectionInputStream(fileInputStream, part.offset, part.localSize)

            val cipher = CipherList.getCipher(file.userMetadata.cipherId)

            val dataInputStream = if (!upload.isEncrypted)
                EncryptInputStream(cipher, file.userMetadata.fileKey, limiter, file.fileMetadata!!.chunkSize)
            else
                limiter

            val md5InputStream = MD5InputStream(dataInputStream)

            val isCancelled = AtomicBoolean()

            subscriber.add(Subscriptions.create {
                isCancelled.set(true)
            })

            val resp = md5InputStream.use {
                uploadClient.uploadPart(userCredentials, upload.id, part.n, part.remoteSize, md5InputStream, isCancelled) { outputStream ->
                    ProgressOutputStream(outputStream) {
                        subscriber.onNext(it)
                    }
                }
            }

            if (resp.checksum != md5InputStream.digestString) {
                //TODO
                //if multipart, just restart upload of current part
                //however if this is a single part, we actually need to delete the file and create a new upload
                throw UploadCorruptedException()
            }
        }
    }
}