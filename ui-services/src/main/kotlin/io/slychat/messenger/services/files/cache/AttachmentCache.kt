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

    //this handles transparently decrypting data on read
    fun getImageStream(fileId: String, fileKey: Key, cipher: Cipher, chunkSize: Int): InputStream?

    fun getDownloadPathForFile(fileId: String): File

    //TODO this should check both in-transit, and on-disk
    /** Returns the list of fileIds which are already present in the cache. */
    fun filterPresent(fileIds: Set<String>): Promise<Set<String>, Exception>

    fun delete(fileIds: List<String>): Promise<Unit, Exception>

    fun markComplete(fileIds: List<String>): Promise<Unit, Exception>
}
