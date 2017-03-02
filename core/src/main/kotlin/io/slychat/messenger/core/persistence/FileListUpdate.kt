package io.slychat.messenger.core.persistence

import io.slychat.messenger.core.files.UserMetadata

sealed class FileListUpdate {
    abstract val fileId: String

    class Delete(override val fileId: String) : FileListUpdate() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as Delete

            if (fileId != other.fileId) return false

            return true
        }

        override fun hashCode(): Int {
            return fileId.hashCode()
        }

        override fun toString(): String {
            return "Delete(fileId='$fileId')"
        }
    }

    class MetadataUpdate(override val fileId: String, val userMetadata: UserMetadata) : FileListUpdate() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as MetadataUpdate

            if (fileId != other.fileId) return false
            if (userMetadata != other.userMetadata) return false

            return true
        }

        override fun hashCode(): Int {
            var result = fileId.hashCode()
            result = 31 * result + userMetadata.hashCode()
            return result
        }

        override fun toString(): String {
            return "MetadataUpdate(fileId='$fileId', userMetadata=$userMetadata)"
        }
    }
}