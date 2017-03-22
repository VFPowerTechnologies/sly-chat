package io.slychat.messenger.core.persistence

import io.slychat.messenger.core.files.RemoteFile

sealed class DirEntry {
    class F(val file: RemoteFile) : DirEntry() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as F

            if (file != other.file) return false

            return true
        }

        override fun hashCode(): Int {
            return file.hashCode()
        }

        override fun toString(): String {
            return "F(file=$file)"
        }
    }

    class D(val fullPath: String, val name: String) : DirEntry() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as D

            if (fullPath != other.fullPath) return false
            if (name != other.name) return false

            return true
        }

        override fun hashCode(): Int {
            var result = fullPath.hashCode()
            result = 31 * result + name.hashCode()
            return result
        }

        override fun toString(): String {
            return "D(fullPath='$fullPath', name='$name')"
        }
    }
}