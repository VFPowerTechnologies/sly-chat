package io.slychat.messenger.services.files

import io.slychat.messenger.core.Quota
import io.slychat.messenger.core.files.RemoteFile
import io.slychat.messenger.core.persistence.DirEntry
import io.slychat.messenger.core.persistence.DownloadInfo
import nl.komponents.kovenant.Promise
import rx.Observable

/**
 * Provides access to all remote file functionality.
 */
interface StorageService {
    /** User's current quota. */
    val quota: Observable<Quota>

    /** Events for transfers. */
    val transferEvents: Observable<TransferEvent>

    /** Events for the remote file list sync itself. */
    val syncEvents: Observable<FileListSyncEvent>

    /** Events regarding modifications to the file list. */
    val fileEvents: Observable<RemoteFileEvent>

    /** All currently active transfers. */
    val transfers: List<TransferStatus>

    fun init()

    fun shutdown()

    /** Initiate a file list sync at the next available opportunity. */
    fun sync()

    /** Fetch information for the given remote file. */
    fun getFile(fileId: String): Promise<RemoteFile?, Exception>

    /** Fetch the give range of files from the file list. Items will always be returned in the same order, but the exact ordering criteria is not to be relied on. */
    fun getFiles(startingAt: Int, count: Int): Promise<List<RemoteFile>, Exception>

    /** Fetch the given range of files in the given directory only. Like getFiles, don't rely on the ordering criteria. */
    fun getFilesAt(startingAt: Int, count: Int, path: String): Promise<List<RemoteFile>, Exception>

    /** Returns info for all given files. The returned map contains all requested ids; if any files are missing, an error is thrown. */
    fun getFilesById(fileIds: List<String>): Promise<Map<String, RemoteFile>, Exception>

    /** Mark the given remote files to be deleted. */
    fun deleteFiles(fileIds: List<String>): Promise<Unit, Exception>

    /** Create an upload for the given local file. */
    fun uploadFile(localFilePath: String, remoteFileDirectory: String, remoteFileName: String, cache: Boolean): Promise<Unit, Exception>

    /** Create downloads for the given files. */
    fun downloadFiles(requests: List<DownloadRequest>): Promise<List<DownloadInfo>, Exception>

    /** Remote the given completed transfers from the transfer list. */
    fun remove(transferIds: List<String>): Promise<Unit, Exception>

    /** Manually retry the given transfer. */
    fun retry(transferId: String): Promise<Unit, Exception>

    /** Cancel the given entries. */
    fun cancel(transferIds: List<String>)

    /** Removes all completed transfers. */
    fun removeCompleted(): Promise<Unit, Exception>

    /** Clears the current sync error. Must not be called while a sync is active. */
    fun clearSyncError()

    /** Will list directories, then files. */
    fun getEntriesAt(startingAt: Int, count: Int, path: String): Promise<List<DirEntry>, Exception>
}