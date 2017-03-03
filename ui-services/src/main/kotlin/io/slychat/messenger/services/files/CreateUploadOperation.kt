package io.slychat.messenger.services.files

import io.slychat.messenger.core.UserCredentials
import io.slychat.messenger.core.crypto.KeyVault
import io.slychat.messenger.core.files.RemoteFile
import io.slychat.messenger.core.files.encryptFileMetadata
import io.slychat.messenger.core.files.encryptUserMetadata
import io.slychat.messenger.core.files.getFilePathHash
import io.slychat.messenger.core.http.api.upload.NewUploadRequest
import io.slychat.messenger.core.http.api.upload.UploadClient
import io.slychat.messenger.core.persistence.Upload

class CreateUploadOperation(
    private val userCredentials: UserCredentials,
    private val upload: Upload,
    private val file: RemoteFile,
    private val keyVault: KeyVault,
    private val uploadClient: UploadClient
) {
    fun run() {
        val fileMetadata = file.fileMetadata ?: error("fileMetadata is null")

        val encryptedUserMetadata = encryptUserMetadata(keyVault, file.userMetadata)
        val encryptedFileMetadata = encryptFileMetadata(file.userMetadata, fileMetadata)

        val partCount = upload.parts.size
        val firstPartSize = upload.parts.first().size
        val lastPart = upload.parts.last()
        val lastPartSize = if (lastPart.size == firstPartSize)
            0
        else
            lastPart.size

        val request = NewUploadRequest(
            upload.id,
            upload.fileId,
            file.shareKey,
            file.remoteFileSize,
            firstPartSize,
            lastPartSize,
            partCount,
            encryptedUserMetadata,
            encryptedFileMetadata,
            getFilePathHash(keyVault, file.userMetadata)
        )

        val resp = uploadClient.newUpload(userCredentials, request)

        //???
        if (!resp.hadSufficientQuota)
            throw InsufficientQuotaException()
    }
}