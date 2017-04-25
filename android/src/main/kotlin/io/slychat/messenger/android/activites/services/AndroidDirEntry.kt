package io.slychat.messenger.android.activites.services

import io.slychat.messenger.core.files.RemoteFile

sealed class AndroidDirEntry {
    class F(var file: RemoteFile) : AndroidDirEntry() {
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

    class D(val fullPath: String, val name: String) : AndroidDirEntry() {
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