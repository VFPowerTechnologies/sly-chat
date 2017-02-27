package io.slychat.messenger.core.http.api.upload

import com.fasterxml.jackson.annotation.JsonProperty
import java.util.*

class UploadInfo(
    @JsonProperty("id")
    val id: String,
    @JsonProperty("device")
    val device: Int,
    @JsonProperty("fileId")
    val fileId: String,
    @JsonProperty("fileSize")
    val fileSize: Long,
    @JsonProperty("userMetadata")
    val userMetadata: ByteArray,
    @JsonProperty("fileMetadata")
    val fileMetadata: ByteArray,
    @JsonProperty("parts")
    val parts: List<UploadPartInfo>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false

        other as UploadInfo

        if (id != other.id) return false
        if (device != other.device) return false
        if (fileId != other.fileId) return false
        if (fileSize != other.fileSize) return false
        if (!Arrays.equals(userMetadata, other.userMetadata)) return false
        if (!Arrays.equals(fileMetadata, other.fileMetadata)) return false
        if (parts != other.parts) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + device
        result = 31 * result + fileId.hashCode()
        result = 31 * result + fileSize.hashCode()
        result = 31 * result + Arrays.hashCode(userMetadata)
        result = 31 * result + Arrays.hashCode(fileMetadata)
        result = 31 * result + parts.hashCode()
        return result
    }

    override fun toString(): String {
        return "UploadInfo(id='$id', device=$device, fileId='$fileId', fileSize=$fileSize, userMetadata=${Arrays.toString(userMetadata)}, fileMetadata=${Arrays.toString(fileMetadata)}, parts=$parts)"
    }
}