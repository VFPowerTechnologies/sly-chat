package io.slychat.messenger.services.files

import io.slychat.messenger.core.persistence.Download
import io.slychat.messenger.core.persistence.TransferError
import io.slychat.messenger.core.persistence.Upload

sealed class Transfer {
    abstract val isUpload: Boolean
    abstract val id: String
    abstract val error: TransferError?
    abstract val localDisplayName: String
    abstract val remoteDisplayName: String

    data class U(val upload: Upload) : Transfer() {
        override val isUpload: Boolean = true

        override val id: String
            get() = upload.id

        override val error: TransferError?
            get() = upload.error

        override val localDisplayName: String
            get() = upload.displayName

        override val remoteDisplayName: String
            get() = upload.remoteFilePath
    }

    data class D(val download: Download) : Transfer() {
        override val isUpload: Boolean = false

        override val id: String
            get() = download.id

        override val error: TransferError?
            get() = download.error

        override val localDisplayName: String
            get() = download.filePath

        override val remoteDisplayName: String
            get() = download.remoteFilePath
    }
}