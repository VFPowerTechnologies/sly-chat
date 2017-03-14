package io.slychat.messenger.services.files

import io.slychat.messenger.core.UserCredentials
import io.slychat.messenger.core.http.api.upload.UploadClient
import io.slychat.messenger.core.persistence.Upload

class CompeteUploadOperation(
    private val userCredentials: UserCredentials,
    private val upload: Upload,
    private val uploadClient: UploadClient
) {
    init {
        require(!upload.isSinglePart) { "Single part upload given" }
    }

    fun run() {
        return uploadClient.completeUpload(userCredentials, upload.id)
    }
}