package io.slychat.messenger.services.files

import io.slychat.messenger.core.files.RemoteFile

sealed class RemoteFileEvent {
    class Added(val files: List<RemoteFile>) : RemoteFileEvent() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as Added

            if (files != other.files) return false

            return true
        }

        override fun hashCode(): Int {
            return files.hashCode()
        }

        override fun toString(): String {
            return "Added(files=$files)"
        }
    }

    /**
     * Indicates a file was deleted (locally or remotely).
     *
     * Note that you may receive this for an already deleted file; when a file is deleted locally, this is emitted.
     * Once the delete has been pushed remotely, this is then emitted again due to the sync.
     */
    class Deleted(val files: List<RemoteFile>) : RemoteFileEvent() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as Deleted

            if (files != other.files) return false

            return true
        }

        override fun hashCode(): Int {
            return files.hashCode()
        }

        override fun toString(): String {
            return "Deleted(files=$files)"
        }
    }

    class Updated(val files: List<RemoteFile>) : RemoteFileEvent() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as Updated

            if (files != other.files) return false

            return true
        }

        override fun hashCode(): Int {
            return files.hashCode()
        }

        override fun toString(): String {
            return "Updated(files=$files)"
        }
    }
}