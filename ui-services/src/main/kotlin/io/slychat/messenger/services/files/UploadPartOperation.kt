package io.slychat.messenger.services.files

import io.slychat.messenger.core.MD5InputStream
import io.slychat.messenger.core.ProgressInputStream
import io.slychat.messenger.core.SectionInputStream
import io.slychat.messenger.core.UserCredentials
import io.slychat.messenger.core.crypto.EncryptInputStream
import io.slychat.messenger.core.crypto.ciphers.CipherList
import io.slychat.messenger.core.files.RemoteFile
import io.slychat.messenger.core.http.api.upload.UploadClient
import io.slychat.messenger.core.persistence.Upload
import io.slychat.messenger.core.persistence.UploadPart
import rx.Subscriber
import java.util.concurrent.atomic.AtomicBoolean

class UploadPartOperation(
    private val fileAccess: PlatformFileAccess,
    private val userCredentials: UserCredentials,
    private val upload: Upload,
    private val part: UploadPart,
    private val file: RemoteFile,
    private val uploadClient: UploadClient,
    private val subscriber: Subscriber<in Long>,
    private val isCancelled: AtomicBoolean
) {
    fun run() {
        val filePath = upload.cachePath ?: upload.filePath

        fileAccess.openFileForRead(filePath) { fileInputStream ->
            val limiter = SectionInputStream(fileInputStream, part.offset, part.localSize)

            val cipher = CipherList.getCipher(file.userMetadata.cipherId)

            val encryptInputStream = if (!upload.isEncrypted)
                EncryptInputStream(cipher, file.userMetadata.fileKey, limiter, file.fileMetadata!!.chunkSize)
            else
                limiter

            //we want progress for the remote size, so listen for progress on the encrypted stream
            //this isn't completely accurate since it excludes the http overhead, as well as not being accurate
            //time-wise due to output buffering but the difference is negligible
            val progressInputStream = ProgressInputStream(encryptInputStream) {
                subscriber.onNext(it.toLong())
            }

            val md5InputStream = MD5InputStream(progressInputStream)

            val resp = md5InputStream.use {
                uploadClient.uploadPart(userCredentials, upload.id, part.n, part.remoteSize, md5InputStream, isCancelled)
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