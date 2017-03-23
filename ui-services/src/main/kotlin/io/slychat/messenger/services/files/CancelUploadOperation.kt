package io.slychat.messenger.services.files

import io.slychat.messenger.core.UserCredentials
import io.slychat.messenger.core.http.api.upload.UploadClient
import io.slychat.messenger.core.persistence.Upload
import io.slychat.messenger.core.persistence.UploadState

class CancelUploadOperation(
    private val userCredentials: UserCredentials,
    private val upload: Upload,
    private val client: UploadClient
) {
    init {
        require(upload.state == UploadState.CANCELLING) { "Require an Upload in cancelling state, got ${upload.state}" }
    }
    fun run() {
        client.cancel(userCredentials, upload.id)
    }
}