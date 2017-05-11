package io.slychat.messenger.services.files.cache

import nl.komponents.kovenant.Promise
import rx.Observable

/**
 * Manages access to the on-disk attachment cache.
 *
 * Handles downloading files, as well as accessing and generating thumbnails.
 */
interface AttachmentCacheManager {
    val events: Observable<AttachmentCacheEvent>

    fun init()

    fun shutdown()

    /**
     * Returns an InputStream to the original image, or null if not on disk.
     * If not on disk, will attempt to fetch it remotely.
     *
     * Must support being called off the main thread.
     */
    fun getImageStream(fileId: String): Promise<ImageLookUpResult, Exception>

    /**
     * Returns an InputStream to the thumbnailed image, or null if not present.
     * If not present, will attempt to generate a thumbnail of the requested size.
     *
     * Must support being called off the main thread.
     */
    fun getThumbnailStream(fileId: String, resolution: Int): Promise<ImageLookUpResult, Exception>

    /**
     * This is used to request caching the original file. Thumbnails are generated on-demand when requested by the UI.
     */
    fun requestCache(fileIds: List<String>): Promise<Unit, Exception>
}