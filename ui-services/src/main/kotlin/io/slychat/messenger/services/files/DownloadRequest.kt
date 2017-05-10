package io.slychat.messenger.services.files

data class DownloadRequest(val fileId: String, val localFilePath: String, val doDecrypt: Boolean)