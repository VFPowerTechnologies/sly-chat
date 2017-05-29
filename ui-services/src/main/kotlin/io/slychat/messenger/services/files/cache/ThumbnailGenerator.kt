package io.slychat.messenger.services.files.cache

import java.io.InputStream
import java.io.OutputStream

/**
 * Thumbnail generation.
 *
 * Resolution is decided by the UI, as higher DPI screens may request larger thumbnails than normal.
 */
interface ThumbnailGenerator {
    /**
     * Generates a new thumbnail using the originalInputStream as a base, with a thumbnailResolution x thumbnailResolution image.
     *
     * If the image has a smaller width than thumbnailResolution, the image is not resized.
     *
     * Must be thread-safe.
     */
    fun generateThumbnail(originalInputStream: InputStream, thumbnailOutputStream: OutputStream, thumbnailResolution: Int)
}