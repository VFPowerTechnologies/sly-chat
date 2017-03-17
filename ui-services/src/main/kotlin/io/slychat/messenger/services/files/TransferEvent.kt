package io.slychat.messenger.services.files

import io.slychat.messenger.core.persistence.Download
import io.slychat.messenger.core.persistence.Upload

sealed class Transfer {
    abstract val isUpload: Boolean

    class U(val upload: Upload) : Transfer() {
        override val isUpload: Boolean = true

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as U

            if (upload != other.upload) return false

            return true
        }

        override fun hashCode(): Int {
            return upload.hashCode()
        }

        override fun toString(): String {
            return "$upload"
        }
    }

    class D(val download: Download) : Transfer() {
        override val isUpload: Boolean = false

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as D

            if (download != other.download) return false

            return true
        }

        override fun hashCode(): Int {
            return download.hashCode()
        }


        override fun toString(): String {
            return "$download"
        }
    }
}

sealed class TransferEvent {
    class Added(val transfer: Transfer, val state: TransferState) : TransferEvent() {
        constructor(upload: Upload, state: TransferState) : this(Transfer.U(upload), state)
        constructor(download: Download, state: TransferState) : this(Transfer.D(download), state)

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as Added

            if (transfer != other.transfer) return false
            if (state != other.state) return false

            return true
        }

        override fun hashCode(): Int {
            var result = transfer.hashCode()
            result = 31 * result + state.hashCode()
            return result
        }

        override fun toString(): String {
            return "Added(transfer=$transfer, state=$state)"
        }
    }

    class Removed(val transfers: List<Transfer>) : TransferEvent() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as Removed

            if (transfers != other.transfers) return false

            return true
        }

        override fun hashCode(): Int {
            return transfers.hashCode()
        }

        override fun toString(): String {
            return "Removed(transfers=$transfers)"
        }
    }

    class Progress(val transfer: Transfer, val progress: TransferProgress) : TransferEvent() {
        constructor(upload: Upload, progress: UploadTransferProgress) : this(Transfer.U(upload), progress)
        constructor(download: Download, progress: DownloadTransferProgress) : this(Transfer.D(download), progress)

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as Progress

            if (transfer != other.transfer) return false
            if (progress != other.progress) return false

            return true
        }

        override fun hashCode(): Int {
            var result = transfer.hashCode()
            result = 31 * result + progress.hashCode()
            return result
        }

        override fun toString(): String {
            return "Progress(transfer=$transfer, progress=$progress)"
        }
    }

    class StateChanged(val transfer: Transfer, val state: TransferState) : TransferEvent() {
        constructor(upload: Upload, state: TransferState) : this(Transfer.U(upload), state)
        constructor(download: Download, state: TransferState) : this(Transfer.D(download), state)

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as StateChanged

            if (transfer != other.transfer) return false
            if (state != other.state) return false

            return true
        }

        override fun hashCode(): Int {
            var result = transfer.hashCode()
            result = 31 * result + state.hashCode()
            return result
        }

        override fun toString(): String {
            return "StateChanged(transfer=$transfer, state=$state)"
        }
    }
}