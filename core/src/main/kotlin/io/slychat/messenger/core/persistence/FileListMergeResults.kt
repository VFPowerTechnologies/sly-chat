package io.slychat.messenger.core.persistence

import io.slychat.messenger.core.files.RemoteFile

data class FileListMergeResults(
    val added: List<RemoteFile>,
    val deleted: List<RemoteFile>,
    val updated: List<RemoteFile>
)