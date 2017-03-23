package io.slychat.messenger.core.persistence

import io.slychat.messenger.core.files.RemoteFile

data class UploadInfo(
    val upload: Upload,
    val file: RemoteFile?
) {
    init {
        if (file == null && upload.state != UploadState.CANCELLED)
            error("Only cancelled uploads may have file=null")
    }
}