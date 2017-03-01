package io.slychat.messenger.services.files

import io.slychat.messenger.core.UserCredentials
import io.slychat.messenger.core.crypto.DerivedKeyType
import io.slychat.messenger.core.crypto.KeyVault
import io.slychat.messenger.core.crypto.ciphers.CipherList
import io.slychat.messenger.core.crypto.ciphers.encryptBulkData
import io.slychat.messenger.core.files.RemoteFile
import io.slychat.messenger.core.http.api.upload.NewUploadRequest
import io.slychat.messenger.core.http.api.upload.UploadClient
import io.slychat.messenger.core.persistence.Upload
import io.slychat.messenger.core.persistence.sqlite.JSONMapper

class CreateUploadOperation(
    private val userCredentials: UserCredentials,
    private val upload: Upload,
    private val file: RemoteFile,
    private val keyVault: KeyVault,
    private val uploadClient: UploadClient
) {
    fun run() {
        val fileMetadata = file.fileMetadata ?: error("fileMetadata is null")
        val keySpec = keyVault.getDerivedKeySpec(DerivedKeyType.USER_METADATA)

        val mapper = JSONMapper.mapper
        val um = mapper.writeValueAsBytes(file.userMetadata)
        val fm = mapper.writeValueAsBytes(fileMetadata)

        val encryptedUserMetadata = encryptBulkData(keySpec, um)

        val cipher = CipherList.getCipher(fileMetadata.cipherId)

        val encryptedFileMetadata = encryptBulkData(cipher, file.userMetadata.fileKey, fm)

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
            encryptedFileMetadata
        )

        val resp = uploadClient.newUpload(userCredentials, request)

        //???
        if (!resp.hadSufficientQuota)
            throw InsufficientQuotaException()
    }
}