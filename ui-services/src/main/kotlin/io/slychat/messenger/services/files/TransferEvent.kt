package io.slychat.messenger.services.files

import io.slychat.messenger.core.persistence.Upload

sealed class TransferEvent {
    class UploadAdded(val upload: Upload, val state: UploadTransferState) : TransferEvent() {
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

    class UploadStateChanged(val upload: Upload, val state: UploadTransferState) : TransferEvent() {
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
}