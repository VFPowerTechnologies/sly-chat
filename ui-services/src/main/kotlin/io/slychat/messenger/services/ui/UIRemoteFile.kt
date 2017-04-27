package io.slychat.messenger.services.ui

import io.slychat.messenger.core.files.RemoteFile

class UIRemoteFile(
    val id: String,
    val lastUpdateVersion: Long,
    val isDeleted: Boolean,
    val userMetadata: UIUserMetadata,
    val fileMetadata: UIFileMetadata?,
    val creationDate: Long,
    val modificationDate: Long,
    val remoteFileSize: Long
)

fun RemoteFile.toUI(): UIRemoteFile {
    return UIRemoteFile(
        id,
        lastUpdateVersion,
        isDeleted,
        userMetadata.toUI(),
        fileMetadata?.toUI(),
        creationDate,
        modificationDate,
        remoteFileSize
    )
}