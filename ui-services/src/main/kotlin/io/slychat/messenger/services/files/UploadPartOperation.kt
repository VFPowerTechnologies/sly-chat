package io.slychat.messenger.services.files

import io.slychat.messenger.core.MD5InputStream
import io.slychat.messenger.core.ProgressOutputStream
import io.slychat.messenger.core.UserCredentials
import io.slychat.messenger.core.crypto.EncryptInputStream
import io.slychat.messenger.core.files.RemoteFile
import io.slychat.messenger.core.http.api.upload.UploadClient
import io.slychat.messenger.core.persistence.Upload
import io.slychat.messenger.core.persistence.UploadPart
import java.io.FileInputStream
import java.util.concurrent.atomic.AtomicBoolean

class UploadPartOperation(
    private val userCredentials: UserCredentials,
    private val upload: Upload,
    private val part: UploadPart,
    private val file: RemoteFile,
    private val uploadClient: UploadClient,
    private val progressCallback: (Long) -> Unit
) {
    fun run() {
        //TODO handle missing file (FileNotFoundException), and then any other exception that's raise
        FileInputStream(upload.filePath).use { fileInputStream ->
            fileInputStream.skip(part.offset)

            val dataInputStream = if (!upload.isEncrypted)
                EncryptInputStream(file.userMetadata.fileKey.raw, fileInputStream, file.fileMetadata!!.chunkSize)
            else
                fileInputStream

            val md5InputStream = MD5InputStream(dataInputStream)

            val resp = md5InputStream.use {
                uploadClient.uploadPart(userCredentials, upload.id, part.n, part.size, md5InputStream, AtomicBoolean()) { outputStream ->
                    ProgressOutputStream(outputStream, progressCallback)
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