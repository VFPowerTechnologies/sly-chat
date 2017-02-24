package io.slychat.messenger.core.persistence

data class Upload(
    val id: String,
    val fileId: String,
    val state: UploadState,
    //must be a string to handle differences in paths on diff platforms (eg: android URIs)
    val filePath: String,
    //if file at filePath is already encrypted (used when uploading cached inline attachments)
    val isEncrypted: Boolean,
    val error: UploadError?,
    val parts: List<UploadPart>
)