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
    //are in order
    val parts: List<UploadPart>
) {
    init {
        var offset = 0L
        var n = 1

        parts.forEach {
            require(it.n == n) { "UploadPart out of order: expected $n, got ${it.n}"}
            n += 1

            require(it.offset == offset) { "UploadPart with invalid offset: expected $offset, got ${it.offset}" }
            offset += it.size
        }
    }

    val isSinglePart: Boolean
        get() = parts.size == 1

    fun markPartCompleted(partN: Int): Upload {
        require(partN > 0 && partN <= parts.size) { "Invalid part number given: $partN" }

        return copy(
            parts = parts.map {
                if (it.n == partN)
                    it.copy(isComplete = true)
                else
                    it
            }
        )
    }
}