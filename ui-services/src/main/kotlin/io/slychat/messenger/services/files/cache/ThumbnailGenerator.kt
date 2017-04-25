package io.slychat.messenger.services.files.cache

import nl.komponents.kovenant.Promise
import java.io.InputStream
import java.io.OutputStream

//TODO just take an input+output stream and resize; handle the proper generation in manager since we need to access cache for paths
//we should do this only when requested; the actual thumbnail sizes depend on UI (eg: android might request larger thumbnails on higher DPI screens)
interface ThumbnailGenerator {
    fun generateThumbnail(originalInputStream: InputStream, thumbnailOutputStream: OutputStream, thumbnailResolution: Int): Promise<Unit, Exception>
}