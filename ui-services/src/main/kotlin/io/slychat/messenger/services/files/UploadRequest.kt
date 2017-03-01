package io.slychat.messenger.services.files

import io.slychat.messenger.core.files.RemoteFile
import io.slychat.messenger.core.persistence.Upload

data class UploadRequest(
    val upload: Upload,
    val file: RemoteFile
)