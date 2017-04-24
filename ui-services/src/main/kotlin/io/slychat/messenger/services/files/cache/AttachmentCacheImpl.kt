package io.slychat.messenger.services.files.cache

import io.slychat.messenger.core.crypto.DecryptInputStream
import io.slychat.messenger.core.crypto.ciphers.Cipher
import io.slychat.messenger.core.crypto.ciphers.Key
import io.slychat.messenger.core.div
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task
import rx.Observable
import rx.subjects.PublishSubject
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.util.*


//another issue is that we need to persist info regarding attachments that we're currently downloading
//TODO need to track download cancellations and fail attachments

//XXX we need to make sure we don't trigger multiple (possibly concurrent) requests for downloading the file to the cache
//this is problematic, since writes are actually done by others, so we need this component to track internally any transfers
//that're writing to the cache to prevent others from also being started to write to it
//eg: imagine we open a convo, which shows a message which causes a download to be created for a fileId
//then we swap to another convo, which happens to have an attachment to the same fileId; we'd start a conflicting download
//to the same path for the same fileId
//XXX this is also bad because if someone requests an ongoing upload/download, it'll "succeed" because the file exists
//we can't dl to a separate file than move it on completion cause the android api doesn't allow it for content URIs
//we also need to be conscious that a user/os may delete some/all of these cache files
//TODO thumbnailgenerator
class AttachmentCacheImpl(
    private val cacheRoot: File
) : AttachmentCache {
    private val subject = PublishSubject.create<AttachmentCacheEvent>()
    override val events: Observable<AttachmentCacheEvent>
        get() = subject

    private val pendingDir = cacheRoot / "pending"
    private val activeDir = cacheRoot / "active"

    fun init() {
        pendingDir.mkdirs()
        activeDir.mkdirs()
    }

    fun getCachePathForFile(fileId: String): File {
        TODO()
    }

    override fun getDownloadPathForFile(fileId: String): File {
        return cacheRoot / fileId
    }

    override fun filterPresent(fileIds: Set<String>): Promise<Set<String>, Exception> {
        return task {
            fileIds.filterTo(HashSet()) { (activeDir / it).isFile }
        }
    }

    override fun getImageStream(fileId: String, fileKey: Key, cipher: Cipher, chunkSize: Int): InputStream? {
        return try {
            DecryptInputStream(
                cipher,
                fileKey,
                FileInputStream(getDownloadPathForFile(fileId)),
                chunkSize
            )
        }
        catch (e: FileNotFoundException) {
            null
        }
    }

    override fun delete(fileIds: List<String>): Promise<Unit, Exception> {
        TODO()
    }

    override fun markComplete(fileIds: List<String>): Promise<Unit, Exception> {
        TODO()
    }
}