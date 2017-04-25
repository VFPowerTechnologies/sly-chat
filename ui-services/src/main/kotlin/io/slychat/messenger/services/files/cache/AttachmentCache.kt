package io.slychat.messenger.services.files.cache

import io.slychat.messenger.core.crypto.ciphers.Cipher
import io.slychat.messenger.core.crypto.ciphers.Key
import nl.komponents.kovenant.Promise
import rx.Observable
import java.io.File
import java.io.InputStream

//XXX in order to support avatars, this should be something like EncryptedFileCache so we can reuse it
//low-level interface for manipulating attachment cache
//we still need the manager to monitor message delete events and update the cache
/**
 * Interface to disk cache.
 */
interface AttachmentCache {
    val events: Observable<AttachmentCacheEvent>

    /** Whether or not the original file is present in the cache. */
    fun isOriginalPresent(fileId: String): Boolean

    /** Return an InputStream for the original image, or null if it doesn't exist. */
    fun getOriginalImageInputStream(fileId: String, fileKey: Key, cipher: Cipher, chunkSize: Int): InputStream?

    /** Return an InputStream for the image at the given resolution, or null if it doesn't exist. */
    fun getThumbnailInputStream(fileId: String, resolution: Int, fileKey: Key, cipher: Cipher, chunkSize: Int): InputStream

    /** Return streams for generating a thumbnail for at the given resolution for a file. */
    fun getThumbnailGenerationStreams(fileId: String, resolution: Int, fileKey: Key, cipher: Cipher, chunkSize: Int): ThumbnailWriteStreams

    fun getDownloadPathForFile(fileId: String): File

    //TODO this should check both in-transit, and on-disk
    /** Returns the list of fileIds which are already present in the cache. */
    fun filterPresent(fileIds: Set<String>): Promise<Set<String>, Exception>

    fun delete(fileIds: List<String>): Promise<Unit, Exception>

    fun markComplete(fileIds: List<String>): Promise<Unit, Exception>
}
