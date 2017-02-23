package io.slychat.messenger.core.http.api.upload

class NewUploadRequest(
    val uploadId: String,
    val shareKey: String,
    val fileSize: Long,
    val partSize: Long,

    //this is the total encrypted file size
    val finalPartSize: Long,

    val partCount: Int,
    //0 if evenly divided into parts, otherwise the size of the last part
    val userMetadata: ByteArray,
    val fileMetadata: ByteArray
) {
    init {
        val evenCount = if (finalPartSize == 0L) partCount else partCount - 1

        val calc = (evenCount * partSize) + finalPartSize
        require(fileSize == calc) { "Part sizes don't add up to file size; got $fileSize, expected $calc" }
    }
}