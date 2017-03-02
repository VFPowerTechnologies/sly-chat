package io.slychat.messenger.core.http.api.storage

import com.fasterxml.jackson.annotation.JsonProperty
import io.slychat.messenger.core.crypto.KeyVault
import io.slychat.messenger.core.files.RemoteFile
import io.slychat.messenger.core.files.decryptFileMetadata
import io.slychat.messenger.core.files.decryptUserMetadata
import java.util.*

class FileInfo(
    @JsonProperty("id")
    val id: String,
    @JsonProperty("shareKey")
    val shareKey: String,
    @param:JsonProperty("isDeleted")
    @get:JsonProperty("isDeleted")
    val isDeleted: Boolean,
    @JsonProperty("lastUpdateVersion")
    val lastUpdateVersion: Long,
    @JsonProperty("creationDate")
    val creationDate: Long,
    @JsonProperty("modificationDate")
    val modificationDate: Long,
    @JsonProperty("userMetadata")
    val userMetadata: ByteArray,
    //null if isDeleted == true
    @JsonProperty("fileMetadata")
    val fileMetadata: ByteArray?,
    @JsonProperty("size")
    val size: Long
) {
    fun toRemoteFile(keyVault: KeyVault): RemoteFile {
        val userMetadata = decryptUserMetadata(keyVault, userMetadata)

        val fileMetadata = fileMetadata?.let { decryptFileMetadata(userMetadata, it) }

        return RemoteFile(
            id,
            shareKey,
            lastUpdateVersion,
            isDeleted,
            userMetadata,
            fileMetadata,
            creationDate,
            modificationDate,
            size
        )
    }

    override fun toString(): String {
        return "FileInfo(id='$id', shareKey='$shareKey', isDeleted=$isDeleted, lastUpdateVersion=$lastUpdateVersion, creationDate=$creationDate, modificationDate=$modificationDate, userMetadata=${Arrays.toString(userMetadata)}, fileMetadata=${Arrays.toString(fileMetadata)}, size=$size)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false

        other as FileInfo

        if (id != other.id) return false
        if (shareKey != other.shareKey) return false
        if (isDeleted != other.isDeleted) return false
        if (lastUpdateVersion != other.lastUpdateVersion) return false
        if (creationDate != other.creationDate) return false
        if (modificationDate != other.modificationDate) return false
        if (!Arrays.equals(userMetadata, other.userMetadata)) return false
        if (!Arrays.equals(fileMetadata, other.fileMetadata)) return false
        if (size != other.size) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + shareKey.hashCode()
        result = 31 * result + isDeleted.hashCode()
        result = 31 * result + lastUpdateVersion.hashCode()
        result = 31 * result + creationDate.hashCode()
        result = 31 * result + modificationDate.hashCode()
        result = 31 * result + Arrays.hashCode(userMetadata)
        result = 31 * result + (fileMetadata?.let { Arrays.hashCode(it) } ?: 0)
        result = 31 * result + size.hashCode()
        return result
    }
}
