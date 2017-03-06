package io.slychat.messenger.core.persistence

import io.slychat.messenger.core.files.RemoteFile

data class DownloadInfo(
    val download: Download,
    val file: RemoteFile
)