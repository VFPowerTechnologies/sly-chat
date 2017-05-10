package io.slychat.messenger.services.files.cache

import io.slychat.messenger.core.crypto.ciphers.Cipher
import io.slychat.messenger.core.crypto.ciphers.Key
import nl.komponents.kovenant.Promise
import java.io.File
import java.io.InputStream

//XXX in order to support avatars, this should be something like EncryptedFileCache so we can reuse it
//low-level interface for manipulating attachment cache
//we still need the manager to monitor message delete events and update the cache
/**
 * Interface to disk cache.
 *
 * Resolutions are taken to be squares of the given value. Aspect ratio is accounted for.
 *
 * Methods must be thread-safe. However, you can assume methods won't be called for the same fileIds concurrently.
 *
 * As the OS could delete files at any time, this must not keep state regarding available files.
 */
interface AttachmentCache {
    /** Create cache directory structure. */
    fun init()

    /** Whether or not the original file is present in the cache. */
    fun isOriginalPresent(fileId: String): Boolean

    /** Return an InputStream for the original image, or null if it doesn't exist. */
    fun getOriginalImageInputStream(fileId: String, fileKey: Key, cipher: Cipher, chunkSize: Int): InputStream?

    /** Return an InputStream for the image at the given resolution, or null if it doesn't exist. */
    fun getThumbnailInputStream(fileId: String, resolution: Int, fileKey: Key, cipher: Cipher, chunkSize: Int): InputStream?

    /** Return streams for generating a thumbnail for the file at the given resolution for a file. Returns null if the original image can't be found. */
    fun getThumbnailGenerationStreams(fileId: String, resolution: Int, fileKey: Key, cipher: Cipher, chunkSize: Int): ThumbnailWriteStreams?

    /** Returns the temporary download location for a file. */
    fun getPendingPathForFile(fileId: String): File

    /** Returns the final location where the original file will be cached. Used during upload creation. */
    fun getFinalPathForFile(fileId: String): File

    /** Returns the list of fileIds which have original files already present in the cache. This doesn't check thumbnails. */
    fun filterPresent(fileIds: Set<String>): Promise<Set<String>, Exception>

    /** Delete any cached files associated with the given file IDs. The caller is responsible for making sure all streams are closed prior to calling this. */
    fun delete(fileIds: List<String>): Promise<Unit, Exception>

    /**
     * Marks an original file as having been completely downloaded.
     *
     * Call this multiple times is ok; subsequent calls will be ignored.
     */
    fun markOriginalComplete(fileIds: List<String>): Promise<Unit, Exception>

    /**
     * Marks a thumbnail file as having been completely downloaded.
     *
     * Call this multiple times is ok; subsequent calls will be ignored.
     */
    fun markThumbnailComplete(fileId: String, resolution: Int): Promise<Unit, Exception>
}
