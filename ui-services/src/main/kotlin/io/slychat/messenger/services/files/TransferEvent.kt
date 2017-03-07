package io.slychat.messenger.services.files

import io.slychat.messenger.core.persistence.Download
import io.slychat.messenger.core.persistence.Upload

sealed class TransferEvent {
    class UploadAdded(val upload: Upload, val state: TransferState) : TransferEvent() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as UploadAdded

            if (upload != other.upload) return false
            if (state != other.state) return false

            return true
        }

        override fun hashCode(): Int {
            var result = upload.hashCode()
            result = 31 * result + state.hashCode()
            return result
        }

        override fun toString(): String {
            return "UploadAdded(upload=$upload, state=$state)"
        }
    }

    class UploadProgress(val upload : Upload, val transferProgress: UploadTransferProgress) : TransferEvent() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as UploadProgress

            if (upload != other.upload) return false
            if (transferProgress != other.transferProgress) return false

            return true
        }

        override fun hashCode(): Int {
            var result = upload.hashCode()
            result = 31 * result + transferProgress.hashCode()
            return result
        }

        override fun toString(): String {
            return "UploadProgress(upload=$upload, transferProgress=$transferProgress)"
        }
    }

    class UploadStateChanged(val upload: Upload, val state: TransferState) : TransferEvent() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as UploadStateChanged

            if (upload != other.upload) return false
            if (state != other.state) return false

            return true
        }

        override fun hashCode(): Int {
            var result = upload.hashCode()
            result = 31 * result + state.hashCode()
            return result
        }

        override fun toString(): String {
            return "UploadStateChanged(upload=$upload, state=$state)"
        }
    }

    class DownloadAdded(val download: Download, val state: TransferState) : TransferEvent() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as DownloadAdded

            if (download != other.download) return false
            if (state != other.state) return false

            return true
        }

        override fun hashCode(): Int {
            var result = download.hashCode()
            result = 31 * result + state.hashCode()
            return result
        }

        override fun toString(): String {
            return "DownloadAdded(download=$download, state=$state)"
        }
    }

    class DownloadProgress(val downloadId: String, val progress: DownloadTransferProgress) : TransferEvent() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as DownloadProgress

            if (downloadId != other.downloadId) return false
            if (progress != other.progress) return false

            return true
        }

        override fun hashCode(): Int {
            var result = downloadId.hashCode()
            result = 31 * result + progress.hashCode()
            return result
        }

        override fun toString(): String {
            return "DownloadProgress(downloadId='$downloadId', progress=$progress)"
        }
    }

    class DownloadStateChange(val download: Download, val state: TransferState) : TransferEvent() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as DownloadStateChange

            if (download != other.download) return false
            if (state != other.state) return false

            return true
        }

        override fun hashCode(): Int {
            var result = download.hashCode()
            result = 31 * result + state.hashCode()
            return result
        }

        override fun toString(): String {
            return "DownloadStateChange(download=$download, state=$state)"
        }
    }
}