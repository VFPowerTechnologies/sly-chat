package io.slychat.messenger.services.files.cache

//the ui doesn't care if the attachment is received or just sent; for sent, we just need to generate thumbnails
sealed class AttachmentEvent {
    data class ThumbnailReady(val fileId: String, val resolution: Int) : AttachmentEvent()
}