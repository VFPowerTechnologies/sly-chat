package io.slychat.messenger.ios

import io.slychat.messenger.services.files.cache.ThumbnailGenerator
import java.io.InputStream
import java.io.OutputStream

//currently unimplemented
class IOSThumbnailGenerator : ThumbnailGenerator {
    override fun generateThumbnail(originalInputStream: InputStream, thumbnailOutputStream: OutputStream, thumbnailResolution: Int) {
        TODO()
    }
}