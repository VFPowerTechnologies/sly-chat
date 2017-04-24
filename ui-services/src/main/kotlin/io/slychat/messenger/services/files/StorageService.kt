package io.slychat.messenger.services.files

import io.slychat.messenger.core.Quota
import io.slychat.messenger.core.files.RemoteFile
import io.slychat.messenger.core.persistence.DirEntry
import io.slychat.messenger.core.persistence.DownloadInfo
import nl.komponents.kovenant.Promise
import rx.Observable

interface StorageService {
    val quota: Observable<Quota>

    val transferEvents: Observable<TransferEvent>

    val syncEvents: Observable<FileListSyncEvent>

    val fileEvents: Observable<RemoteFileEvent>

    val transfers: List<TransferStatus>

    fun init()

    fun shutdown()

    fun sync()

    fun getFile(fileId: String): Promise<RemoteFile?, Exception>

    fun getFiles(startingAt: Int, count: Int): Promise<List<RemoteFile>, Exception>

    fun getFilesAt(startingAt: Int, count: Int, path: String): Promise<List<RemoteFile>, Exception>

    //if any files are missing, an error is thrown
    fun getFilesById(fileIds: List<String>): Promise<Map<String, RemoteFile>, Exception>

    fun deleteFiles(fileIds: List<String>): Promise<Unit, Exception>

    fun uploadFile(localFilePath: String, remoteFileDirectory: String, remoteFileName: String, cache: Boolean): Promise<Unit, Exception>

    fun downloadFiles(requests: List<DownloadRequest>): Promise<List<DownloadInfo>, Exception>

    fun remove(transferIds: List<String>): Promise<Unit, Exception>

    fun retry(transferId: String): Promise<Unit, Exception>

    fun cancel(transferIds: List<String>)

    fun removeCompleted(): Promise<Unit, Exception>

    /** Must not be called during a sync. */
    fun clearSyncError()

    /** Will list directories, then files. */
    fun getEntriesAt(startingAt: Int, count: Int, path: String): Promise<List<DirEntry>, Exception>

    fun filterOwnedFiles(fileIds: List<String>): Promise<List<String>, Exception>
}