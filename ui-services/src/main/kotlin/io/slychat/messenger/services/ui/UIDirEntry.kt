package io.slychat.messenger.services.ui

import io.slychat.messenger.core.persistence.DirEntry

enum class UIDirEntryType {
    FILE,
    DIR
}

sealed class UIDirEntry {
    abstract val type: UIDirEntryType

    class F(val file: UIRemoteFile) : UIDirEntry() {
        override val type: UIDirEntryType
            get() = UIDirEntryType.FILE
    }

    class D(val fullPath: String, val name: String) : UIDirEntry() {
        override val type: UIDirEntryType
            get() = UIDirEntryType.DIR
    }
}

fun DirEntry.toUI(): UIDirEntry {
    return when (this) {
        is DirEntry.F -> UIDirEntry.F(file.toUI())
        is DirEntry.D -> UIDirEntry.D(fullPath, name)
    }
}