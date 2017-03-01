package io.slychat.messenger.core.files

data class RemoteFile(
    val id: String,
    val shareKey: String,
    val lastUpdateVersion: Int,
    //only used for remote entries returned
    val isDeleted: Boolean,
    val userMetadata: UserMetadata,
    val fileMetadata: FileMetadata?,
    val creationDate: Long,
    val modificationDate: Long,
    val remoteFileSize: Long
) {
    init {
        if (fileMetadata == null && !isDeleted)
            error("File is not deleted but has empty fileMetadata")
    }
}