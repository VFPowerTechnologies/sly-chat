package io.slychat.messenger.core.persistence

import io.slychat.messenger.core.files.RemoteFile

data class UploadInfo(
    val upload: Upload,
    val file: RemoteFile
)