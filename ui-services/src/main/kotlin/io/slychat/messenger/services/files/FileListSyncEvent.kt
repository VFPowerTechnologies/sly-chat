package io.slychat.messenger.services.files

sealed class FileListSyncEvent {
    class Begin : FileListSyncEvent() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            return other?.javaClass == javaClass
        }

        override fun hashCode(): Int {
            return 0
        }

        override fun toString(): String {
            return "Begin()"
        }
    }

    class Error : FileListSyncEvent() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            return other?.javaClass == javaClass
        }

        override fun hashCode(): Int {
            return 0
        }

        override fun toString(): String {
            return "Error()"
        }
    }

    class End(val result: StorageSyncResult) : FileListSyncEvent() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as End

            if (result != other.result) return false

            return true
        }

        override fun hashCode(): Int {
            return result.hashCode()
        }

        override fun toString(): String {
            return "End(result=$result)"
        }
    }
}